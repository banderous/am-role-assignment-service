package uk.gov.hmcts.reform.roleassignment.domain.service.createroles;

import org.jetbrains.annotations.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.roleassignment.data.roleassignment.HistoryEntity;
import uk.gov.hmcts.reform.roleassignment.data.roleassignment.RequestEntity;
import uk.gov.hmcts.reform.roleassignment.domain.model.AssignmentRequest;
import uk.gov.hmcts.reform.roleassignment.domain.model.Request;
import uk.gov.hmcts.reform.roleassignment.domain.model.RoleAssignment;
import uk.gov.hmcts.reform.roleassignment.domain.model.enums.RequestType;
import uk.gov.hmcts.reform.roleassignment.domain.model.enums.Status;
import uk.gov.hmcts.reform.roleassignment.domain.service.common.ParseRequestService;
import uk.gov.hmcts.reform.roleassignment.domain.service.common.PersistenceService;
import uk.gov.hmcts.reform.roleassignment.domain.service.common.PrepareResponseService;
import uk.gov.hmcts.reform.roleassignment.domain.service.common.ValidationModelService;
import uk.gov.hmcts.reform.roleassignment.util.PersistenceUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;


@Service
public class CreateRoleAssignmentOrchestrator {

    private ParseRequestService parseRequestService;
    private PersistenceService persistenceService;
    private ValidationModelService validationModelService;
    private PersistenceUtil persistenceUtil;
    private PrepareResponseService prepareResponseService;
    Request request;
    RequestEntity requestEntity;
    List<UUID> emptyUUIds = new ArrayList<>();

    public CreateRoleAssignmentOrchestrator(ParseRequestService parseRequestService,
                                            PersistenceService persistenceService,
                                            ValidationModelService validationModelService,
                                            PersistenceUtil persistenceUtil,
                                            PrepareResponseService prepareResponseService) {
        this.parseRequestService = parseRequestService;
        this.persistenceService = persistenceService;
        this.validationModelService = validationModelService;
        this.persistenceUtil = persistenceUtil;
        this.prepareResponseService = prepareResponseService;
    }

    public ResponseEntity<Object> createRoleAssignment(AssignmentRequest roleAssignmentRequest) throws Exception {

        AssignmentRequest existingAssignmentRequest;

        //1. call parse request service
        AssignmentRequest parsedAssignmentRequest = parseRequestService
            .parseRequest(roleAssignmentRequest, RequestType.CREATE);

        //2. Call persistence service to store only the request
        requestEntity = persistInitialRequest(parsedAssignmentRequest.getRequest());
        requestEntity.setHistoryEntities(new HashSet<>());
        request = parsedAssignmentRequest.getRequest();
        request.setId(requestEntity.getId());

        //Check replace existing true/false
        if (request.isReplaceExisting()) {

            //retrieve existing assignments and prepared temp request
            existingAssignmentRequest = retrieveExistingAssignments(parsedAssignmentRequest);

            //validation
            evaluateDeleteAssignments(existingAssignmentRequest, parsedAssignmentRequest);

            //Checking all assignments has DELETE_APPROVED status to create new entries of assignment records
            checkAllDeleteApproved(existingAssignmentRequest, parsedAssignmentRequest);

        } else {


            //Save requested role in history table with CREATED and Approved Status
            createNewAssignmentRecords(parsedAssignmentRequest);

            checkAllApproved(parsedAssignmentRequest);


        }


        //8. Call the persistence to copy assignment records to RoleAssignmentLive table
        ResponseEntity<Object> result = prepareResponseService.prepareCreateRoleResponse(parsedAssignmentRequest);

        parseRequestService.removeCorrelationLog();
        return result;
    }

    @NotNull
    private AssignmentRequest retrieveExistingAssignments(AssignmentRequest parsedAssignmentRequest) {
        AssignmentRequest existingAssignmentRequest;
        List<RoleAssignment> existingAssignments = persistenceService.getAssignmentsByProcess(
            request.process,
            request.reference,
            Status.LIVE.toString()
        );

        //create a new existing assignment request for delete records
        existingAssignmentRequest = new AssignmentRequest(new Request(), Collections.emptyList());
        existingAssignmentRequest.setRequest(parsedAssignmentRequest.getRequest());
        existingAssignmentRequest.setRequestedRoles(existingAssignments);
        return existingAssignmentRequest;
    }

    private void evaluateDeleteAssignments(AssignmentRequest existingAssignmentRequest,
                                           AssignmentRequest parsedAssignmentRequest) throws Exception {
        //calling drools rules for validation
        validationModelService.validateRequest(existingAssignmentRequest);

        // we are mocking delete rejected status
        checkDeleteApproved(existingAssignmentRequest);


    }

    private void checkAllApproved(AssignmentRequest parsedAssignmentRequest) {

        // decision block
        List<RoleAssignment> createApprovedAssignments = parsedAssignmentRequest.getRequestedRoles().stream()
            .filter(role -> role.getStatus().equals(
                Status.APPROVED)).collect(
                Collectors.toList());

        if (createApprovedAssignments.size() == parsedAssignmentRequest.getRequestedRoles().size()) {
            executeCreateRequest(parsedAssignmentRequest);


        } else {
            List<UUID> rejectedAssignmentIds = parsedAssignmentRequest.getRequestedRoles().stream()
                .filter(role -> role.getStatus().equals(
                    Status.REJECTED)).map(obj -> obj.getId()).collect(
                    Collectors.toList());
            rejectCreateRequest(parsedAssignmentRequest, rejectedAssignmentIds);


        }
    }

    private void rejectCreateRequest(AssignmentRequest parsedAssignmentRequest, List<UUID> rejectedAssignmentIds) {
        // Insert parsedAssignmentRequest.getRequestedRoles() records into history table with status REJECTED
        insertRequestedRole(parsedAssignmentRequest, Status.REJECTED, rejectedAssignmentIds);

        // Update request status to REJECTED
        request.setStatus(Status.REJECTED);
        requestEntity.setStatus(Status.REJECTED.toString());
        if (!rejectedAssignmentIds.isEmpty()) {
            requestEntity.setLog("Request has been rejected due to following assignment Ids :"
                                     + rejectedAssignmentIds.toString());
            request.setLog("Request has been rejected due to following assignment Ids :"
                               + rejectedAssignmentIds.toString());
        }


        persistenceService.persistRequestToHistory(requestEntity);
    }

    private void executeCreateRequest(AssignmentRequest parsedAssignmentRequest) {
        // Insert parsedAssignmentRequest.getRequestedRoles() records into live table
        moveHistoryRecordsToLiveTable(requestEntity);

        // Insert parsedAssignmentRequest.getRequestedRoles() records into history table with status LIVE
        insertRequestedRole(parsedAssignmentRequest, Status.LIVE, emptyUUIds);

        // Update request status to approved
        request.setStatus(Status.APPROVED);
        requestEntity.setLog(request.getLog());
        requestEntity.setStatus(Status.APPROVED.toString());
        persistenceService.persistRequestToHistory(requestEntity);
    }

    private void checkAllDeleteApproved(AssignmentRequest existingAssignmentRequest,
                                        AssignmentRequest parsedAssignmentRequest) throws Exception {
        // decision block
        List<RoleAssignment> deleteApprovedAssignments = existingAssignmentRequest.getRequestedRoles().stream()
            .filter(role -> role.getStatus().equals(
                Status.DELETE_APPROVED)).collect(
                Collectors.toList());

        if (deleteApprovedAssignments.size() == existingAssignmentRequest.getRequestedRoles().size()) {

            //Create New Assignment records
            createNewAssignmentRecords(parsedAssignmentRequest);

            // decision block
            List<RoleAssignment> createApprovedAssignments = parsedAssignmentRequest
                .getRequestedRoles().stream()
                .filter(role -> role.getStatus().equals(
                    Status.APPROVED))
                .collect(Collectors.toList());

            if (createApprovedAssignments.size() == parsedAssignmentRequest.getRequestedRoles().size()) {

                executeReplaceRequest(existingAssignmentRequest, parsedAssignmentRequest);


            } else {
                List<UUID> rejectedAssignmentIds = parsedAssignmentRequest.getRequestedRoles().stream()
                    .filter(role -> role.getStatus().equals(
                        Status.REJECTED)).map(obj -> obj.getId()).collect(
                        Collectors.toList());
                rejectReplaceRequest(existingAssignmentRequest, parsedAssignmentRequest, rejectedAssignmentIds);

            }

        } else {
            List<UUID> rejectedAssignmentIds = existingAssignmentRequest.getRequestedRoles().stream()
                .filter(role -> role.getStatus().equals(
                    Status.DELETE_REJECTED)).map(obj -> obj.getId()).collect(
                    Collectors.toList());


            rejectDeleteRequest(existingAssignmentRequest, rejectedAssignmentIds, parsedAssignmentRequest);

        }
    }

    private void rejectDeleteRequest(AssignmentRequest existingAssignmentRequest,
                                     List<UUID> rejectedAssignmentIds,
                                     AssignmentRequest parsedAssignmentRequest) {
        //Insert existingAssignmentRequest.getRequestedRoles() records into history table with status deleted-Rejected
        insertRequestedRole(existingAssignmentRequest, Status.DELETE_REJECTED, rejectedAssignmentIds);

        // Insert parsedAssignmentRequest.getRequestedRoles() records into history table with status REJECTED
        insertRequestedRole(parsedAssignmentRequest, Status.REJECTED, rejectedAssignmentIds);
        // Update request status to REJECTED
        request.setStatus(Status.REJECTED);
        requestEntity.setStatus(Status.REJECTED.toString());
        if (!rejectedAssignmentIds.isEmpty()) {
            requestEntity.setLog("Request has been rejected due to following assignment Ids :"
                                     + rejectedAssignmentIds.toString());
            request.setLog("Request has been rejected due to following assignment Ids :"
                               + rejectedAssignmentIds.toString());
        }

        persistenceService.persistRequestToHistory(requestEntity);
    }

    private void rejectReplaceRequest(AssignmentRequest existingAssignmentRequest,
                                      AssignmentRequest parsedAssignmentRequest, List<UUID> rejectedAssignmentIds) {
        //Insert existingAssignmentRequest.getRequestedRoles() records into history table with status deleted-Rejected
        insertRequestedRole(existingAssignmentRequest, Status.DELETE_REJECTED, rejectedAssignmentIds);

        // Insert parsedAssignmentRequest.getRequestedRoles() records into history table with status REJECTED
        insertRequestedRole(parsedAssignmentRequest, Status.REJECTED, rejectedAssignmentIds);

        // Update request status to REJECTED
        request.setStatus(Status.REJECTED);
        requestEntity.setStatus(Status.REJECTED.toString());
        if (!rejectedAssignmentIds.isEmpty()) {
            requestEntity.setLog("Request has been rejected due to following assignment Ids :"
                                     + rejectedAssignmentIds.toString());
            request.setLog("Request has been rejected due to following assignment Ids :"
                               + rejectedAssignmentIds.toString());
        }

        persistenceService.persistRequestToHistory(requestEntity);
    }

    private void executeReplaceRequest(AssignmentRequest existingAssignmentRequest,
                                       AssignmentRequest parsedAssignmentRequest) {
        //delete existingAssignmentRequest.getRequestedRoles() records from live table--Hard delete
        deleteLiveAssignments(existingAssignmentRequest.getRequestedRoles());

        //Insert existingAssignmentRequest.getRequestedRoles() records into history table with status deleted-soft
        // delete
        insertRequestedRole(existingAssignmentRequest, Status.DELETED, emptyUUIds);


        // Insert parsedAssignmentRequest.getRequestedRoles() records into live table
        moveHistoryRecordsToLiveTable(requestEntity);

        // Insert parsedAssignmentRequest.getRequestedRoles() records into history table with status LIVE
        insertRequestedRole(parsedAssignmentRequest, Status.LIVE, emptyUUIds);

        // Update request status to approved
        request.setStatus(Status.APPROVED);
        requestEntity.setStatus(Status.APPROVED.toString());
        requestEntity.setLog(request.getLog());
        persistenceService.persistRequestToHistory(requestEntity);
    }

    private void checkDeleteApproved(AssignmentRequest existingAssignmentRequest) {
        for (RoleAssignment requestedAssignment : existingAssignmentRequest.getRequestedRoles()) {
            requestedAssignment.setRequest(existingAssignmentRequest.getRequest());
            if (!requestedAssignment.getStatus().equals(Status.APPROVED)) {
                requestedAssignment.status = Status.DELETE_REJECTED;
                requestedAssignment.statusSequence = Status.DELETE_REJECTED.sequence;
            } else {
                requestedAssignment.status = Status.DELETE_APPROVED;
                requestedAssignment.statusSequence = Status.DELETE_APPROVED.sequence;
            }
            // persist history in db
            requestEntity.getHistoryEntities().add(persistenceService.persistHistory(requestedAssignment, request));

        }

        //Persist request to update relationship with history entities
        persistenceService.persistRequestToHistory(requestEntity);
    }

    //Create New Assignment Records
    private void createNewAssignmentRecords(AssignmentRequest parsedAssignmentRequest) throws Exception {
        //Save new requested role in history table with CREATED Status

        insertRequestedRole(parsedAssignmentRequest, Status.CREATED, emptyUUIds);

        validationModelService.validateRequest(parsedAssignmentRequest);

        //Save requested role in history table with APPROVED/REJECTED Status
        for (RoleAssignment requestedAssignment : parsedAssignmentRequest.getRequestedRoles()) {
            requestedAssignment.setRequest(parsedAssignmentRequest.getRequest());
            requestEntity.getHistoryEntities().add(persistenceService.persistHistory(requestedAssignment, request));
        }

        //Persist request to update relationship with history entities
        persistenceService.persistRequestToHistory(requestEntity);
    }

    private void moveHistoryRecordsToLiveTable(RequestEntity requestEntity) {
        List<HistoryEntity> historyEntities = requestEntity.getHistoryEntities()
            .stream()
            .filter(entity -> entity.getStatus().equals(
                Status.APPROVED.toString()))
            .collect(Collectors.toList());

        List<RoleAssignment> roleAssignments = historyEntities.stream().map(entity -> persistenceUtil
            .convertHistoryEntityToRoleAssignment(
                entity)).collect(
            Collectors.toList());
        for (RoleAssignment requestedAssignment : roleAssignments) {
            requestedAssignment.setStatus(Status.LIVE);
            persistenceService.persistRoleAssignment(requestedAssignment);
            persistenceService.persistActorCache(requestedAssignment);
        }
    }


    private RequestEntity persistInitialRequest(Request request) {
        return persistenceService.persistRequest(request);
    }

    private void deleteLiveAssignments(Collection<RoleAssignment> existingAssignments) {
        for (RoleAssignment requestedRole : existingAssignments) {
            persistenceService.deleteRoleAssignment(requestedRole);
        }
    }

    private void insertRequestedRole(AssignmentRequest assignmentRequest,
                                     Status status,
                                     List<UUID> rejectedAssignmentIds) {
        for (RoleAssignment requestedAssignment : assignmentRequest.getRequestedRoles()) {
            requestedAssignment.setRequest(assignmentRequest.getRequest());
            if (!rejectedAssignmentIds.isEmpty()
                && (status.equals(Status.REJECTED)
                || status.equals(Status.DELETE_REJECTED))
                && (requestedAssignment.getStatus().equals(Status.APPROVED)
                || requestedAssignment.getStatus().equals(Status.CREATED)
                || requestedAssignment.getStatus().equals(Status.DELETE_APPROVED))) {
                requestedAssignment.setLog(
                    "Requested Role has been rejected due to following new/existing assignment Ids :"
                        + rejectedAssignmentIds.toString());
            }
            requestedAssignment.status = status;
            // persist history in db
            HistoryEntity entity = persistenceService.persistHistory(requestedAssignment, request);
            requestedAssignment.setId(entity.getId());
            requestEntity.getHistoryEntities().add(entity);
        }
        //Persist request to update relationship with history entities
        persistenceService.persistRequestToHistory(requestEntity);
    }


}
