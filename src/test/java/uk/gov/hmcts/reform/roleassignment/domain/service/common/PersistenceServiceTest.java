package uk.gov.hmcts.reform.roleassignment.domain.service.common;


import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.hibernate.jdbc.BatchFailedException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import uk.gov.hmcts.reform.roleassignment.controller.advice.exception.ResourceNotFoundException;
import uk.gov.hmcts.reform.roleassignment.data.ActorCacheEntity;
import uk.gov.hmcts.reform.roleassignment.data.ActorCacheRepository;
import uk.gov.hmcts.reform.roleassignment.data.DatabaseChangelogLockEntity;
import uk.gov.hmcts.reform.roleassignment.data.DatabseChangelogLockRepository;
import uk.gov.hmcts.reform.roleassignment.data.FlagConfig;
import uk.gov.hmcts.reform.roleassignment.data.FlagConfigRepository;
import uk.gov.hmcts.reform.roleassignment.data.HistoryEntity;
import uk.gov.hmcts.reform.roleassignment.data.HistoryRepository;
import uk.gov.hmcts.reform.roleassignment.data.RequestEntity;
import uk.gov.hmcts.reform.roleassignment.data.RequestRepository;
import uk.gov.hmcts.reform.roleassignment.data.RoleAssignmentEntity;
import uk.gov.hmcts.reform.roleassignment.data.RoleAssignmentRepository;
import uk.gov.hmcts.reform.roleassignment.domain.model.ActorCache;
import uk.gov.hmcts.reform.roleassignment.domain.model.Assignment;
import uk.gov.hmcts.reform.roleassignment.domain.model.AssignmentRequest;
import uk.gov.hmcts.reform.roleassignment.domain.model.QueryRequest;
import uk.gov.hmcts.reform.roleassignment.domain.model.MultipleQueryRequest;
import uk.gov.hmcts.reform.roleassignment.domain.model.Request;
import uk.gov.hmcts.reform.roleassignment.domain.model.RoleAssignment;
import uk.gov.hmcts.reform.roleassignment.domain.model.enums.RoleCategory;
import uk.gov.hmcts.reform.roleassignment.helper.TestDataBuilder;
import uk.gov.hmcts.reform.roleassignment.util.PersistenceUtil;

import javax.persistence.EntityManager;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static java.time.LocalDateTime.now;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import static uk.gov.hmcts.reform.roleassignment.domain.model.enums.Status.CREATED;
import static uk.gov.hmcts.reform.roleassignment.domain.model.enums.Status.LIVE;

class PersistenceServiceTest {

    @Mock
    private HistoryRepository historyRepository;
    @Mock
    private RequestRepository requestRepository;
    @Mock
    private RoleAssignmentRepository roleAssignmentRepository;
    @Mock
    private PersistenceUtil persistenceUtil;
    @Mock
    private ActorCacheRepository actorCacheRepository;
    @Mock
    private DatabseChangelogLockRepository databseChangelogLockRepository;
    @Mock
    EntityManager entityManager;

    @Mock
    private Page<RoleAssignmentEntity> pageable;

    @Mock
    private FlagConfigRepository flagConfigRepository;


    @InjectMocks
    private final PersistenceService sut = new PersistenceService(
        historyRepository, requestRepository, roleAssignmentRepository, persistenceUtil, actorCacheRepository,
        databseChangelogLockRepository,
        flagConfigRepository
    );


    @Mock
    Specification<RoleAssignmentEntity> mockSpec;

    @Mock
    Root<RoleAssignmentEntity> root;
    @Mock
    CriteriaQuery<RoleAssignmentEntity> query;
    @Mock
    CriteriaBuilder builder;
    @Mock
    Predicate predicate;


    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void persistRequest() {
        Request request = TestDataBuilder.buildRequest(CREATED, false);
        RequestEntity requestEntity = TestDataBuilder.buildRequestEntity(request);
        when(persistenceUtil.convertRequestToEntity(request)).thenReturn(requestEntity);
        when(requestRepository.save(requestEntity)).thenReturn(requestEntity);

        RequestEntity result = sut.persistRequest(request);
        assertNotNull(result);
        verify(persistenceUtil, times(1)).convertRequestToEntity(any(Request.class));
        verify(requestRepository, times(1)).save(requestEntity);
    }

    @Test
    void persistRequestToHistory() {
        Request request = TestDataBuilder.buildRequest(CREATED, false);
        RequestEntity requestEntity = TestDataBuilder.buildRequestEntity(request);
        try {
            sut.updateRequest(requestEntity);
            assertNotNull(requestEntity);
            verify(requestRepository, times(1)).save(requestEntity);

        } catch (Exception e) {
            throw new InternalError(e);
        }
    }


    @Test
    void persistRoleAssignment() throws IOException {
        AssignmentRequest assignmentRequest = TestDataBuilder
            .buildAssignmentRequest(CREATED, LIVE, false);
        RoleAssignmentEntity roleAssignmentEntity = TestDataBuilder.convertRoleAssignmentToEntity(
            assignmentRequest.getRequestedRoles().iterator().next());
        when(persistenceUtil.convertRoleAssignmentToEntity(
            assignmentRequest.getRequestedRoles().iterator().next(), true))
            .thenReturn(roleAssignmentEntity);

        sut.persistRoleAssignments(assignmentRequest.getRequestedRoles());

        verify(persistenceUtil, times(2))
            .convertRoleAssignmentToEntity(any(RoleAssignment.class), any(boolean.class));
        verify(entityManager, times(2)).persist(any());
        verify(entityManager, times(1)).flush();
    }

    @Test
    void persistActorCache() throws IOException, SQLException {
        RoleAssignment roleAssignment = TestDataBuilder.buildRoleAssignment(LIVE);
        ObjectMapper mapper = new ObjectMapper();
        JsonNode rootNode = mapper.createObjectNode();
        ActorCacheEntity entity = new ActorCacheEntity(roleAssignment.getActorId(), 1234, rootNode);
        ActorCacheEntity entity1 = new ActorCacheEntity(roleAssignment.getActorId(), 12, rootNode);
        TestDataBuilder.prepareActorCache(roleAssignment);
        when(persistenceUtil.convertActorCacheToEntity(any())).thenReturn(entity);
        when(actorCacheRepository.findByActorId(roleAssignment.getActorId())).thenReturn(entity1);
        Collection<RoleAssignment> roleAssignmentCollation = new ArrayList<>();
        roleAssignmentCollation.add(roleAssignment);
        sut.persistActorCache(roleAssignmentCollation);

        assertEquals(entity.getActorId(), roleAssignmentCollation.iterator().next().getActorId());
        assertEquals(entity.getEtag(), entity1.getEtag());
        verify(persistenceUtil, times(1)).convertActorCacheToEntity(any());
        verify(actorCacheRepository, times(1)).findByActorId(roleAssignment.getActorId());
        verify(entityManager, times(1)).flush();
    }

    @Test
    void actorCache() throws IOException {
        ActorCache actorCache = sut.prepareActorCache(TestDataBuilder.buildRoleAssignment(LIVE));
        assertEquals("21334a2b-79ce-44eb-9168-2d49a744be9c", actorCache.getActorId());
    }

    @Test
    void persistActorCache_nullEntity() throws IOException, SQLException {
        RoleAssignment roleAssignment = Mockito.spy(TestDataBuilder.buildRoleAssignment(LIVE));
        roleAssignment.setActorId(null);
        ObjectMapper mapper = new ObjectMapper();
        JsonNode rootNode = mapper.createObjectNode();
        ActorCacheEntity entity = Mockito.spy(new ActorCacheEntity(roleAssignment.getActorId(), 1234, rootNode));

        TestDataBuilder.prepareActorCache(roleAssignment);
        when(persistenceUtil.convertActorCacheToEntity(any())).thenReturn(entity);
        when(actorCacheRepository.findByActorId(roleAssignment.getActorId())).thenReturn(null);
        when(actorCacheRepository.save(entity)).thenReturn(entity);
        Collection<RoleAssignment> roleAssignmentCollation = new ArrayList<>();
        roleAssignmentCollation.add(roleAssignment);
        sut.persistActorCache(roleAssignmentCollation);

        assertNull(entity.getActorId());
        verify(roleAssignment, times(6)).getActorId();
        verify(persistenceUtil, times(1)).convertActorCacheToEntity(any());
        verify(actorCacheRepository, times(1)).findByActorId(roleAssignment.getActorId());
        verify(entityManager, times(1)).persist(any());
        verify(entityManager, times(1)).flush();
    }

    @Test
    void getActorCacheEntity() throws IOException, SQLException {
        String id = UUID.randomUUID().toString();
        ActorCacheEntity actorCacheEntity = TestDataBuilder.buildActorCacheEntity();
        when(actorCacheRepository.findByActorId(id)).thenReturn(actorCacheEntity);
        ActorCacheEntity result = sut.getActorCacheEntity(id);
        assertEquals(actorCacheEntity, result);
        verify(actorCacheRepository, times(1)).findByActorId(id);
    }

    @Test
    void getActorCacheEntityException() throws SQLException {
        String uuid = UUID.randomUUID().toString();
        doThrow(SQLException.class).when(actorCacheRepository).findByActorId(any());
        assertThrows(ResponseStatusException.class, () ->
            sut.getActorCacheEntity(uuid));
    }



    @Test
    void getExistingRoleByProcessAndReference() throws IOException {
        AssignmentRequest assignmentRequest = TestDataBuilder
            .buildAssignmentRequest(CREATED, LIVE, false);
        RequestEntity requestEntity = TestDataBuilder.buildRequestEntity(assignmentRequest.getRequest());
        HistoryEntity historyEntity = TestDataBuilder.buildHistoryIntoEntity(
            assignmentRequest.getRequestedRoles().iterator().next(), requestEntity);
        Set<HistoryEntity> historyEntities = new HashSet<>();
        historyEntities.add(historyEntity);

        RoleAssignment requestedRole = TestDataBuilder.convertHistoryEntityInModel(historyEntity);

        when(historyRepository.findByReference(
            "process", "reference", "status")).thenReturn(historyEntities);
        when(persistenceUtil.convertHistoryEntityToRoleAssignment(historyEntity)).thenReturn(requestedRole);

        List<RoleAssignment> result = sut.getAssignmentsByProcess("process", "reference", "status");

        assertNotNull(result);
        assertFalse(result.isEmpty());
        assertFalse(result.contains(null));

        verify(historyRepository, times(1)).findByReference(
            "process", "reference", "status");
        verify(persistenceUtil, times(1)).convertHistoryEntityToRoleAssignment(historyEntity);
    }

    @Test
    void persistHistoryEntities() throws IOException {
        AssignmentRequest assignmentRequest = TestDataBuilder
            .buildAssignmentRequest(CREATED, LIVE, false);
        RequestEntity requestEntity = TestDataBuilder.buildRequestEntity(assignmentRequest.getRequest());
        HistoryEntity historyEntity = TestDataBuilder.buildHistoryIntoEntity(
            assignmentRequest.getRequestedRoles().iterator().next(), requestEntity);
        Set<HistoryEntity> historyEntities = new HashSet<>();
        historyEntities.add(historyEntity);
        sut.persistHistoryEntities(historyEntities);

        verify(entityManager, times(1)).persist(any());
        verify(entityManager, times(1)).flush();
    }


    @Test
    void deleteRoleAssignment() throws IOException {
        AssignmentRequest assignmentRequest = TestDataBuilder
            .buildAssignmentRequest(CREATED, LIVE, false);
        RoleAssignmentEntity roleAssignmentEntity = TestDataBuilder.convertRoleAssignmentToEntity(
            assignmentRequest.getRequestedRoles().iterator().next());

        when(persistenceUtil.convertRoleAssignmentToEntity(
            assignmentRequest.getRequestedRoles().iterator().next(), false)).thenReturn(roleAssignmentEntity);

        sut.deleteRoleAssignment(assignmentRequest.getRequestedRoles().iterator().next());

        verify(persistenceUtil, times(1))
            .convertRoleAssignmentToEntity(any(RoleAssignment.class), any(boolean.class));
        verify(roleAssignmentRepository, times(1)).delete(any(RoleAssignmentEntity.class));
    }

    @Test
    void deleteRoleAssignmentById()  {
        sut.deleteRoleAssignmentByActorId(UUID.randomUUID().toString());
        verify(roleAssignmentRepository, times(1)).deleteByActorId(any(String.class));
    }

    @Test
    void getAssignmentsByActor() throws IOException, SQLException {
        String id = UUID.randomUUID().toString();
        Set<RoleAssignmentEntity> roleAssignmentEntitySet = new HashSet<>();
        roleAssignmentEntitySet.add(TestDataBuilder.buildRoleAssignmentEntity(TestDataBuilder
                                                                                  .buildRoleAssignment(LIVE)));
        when(roleAssignmentRepository.findByActorId(id))
            .thenReturn(roleAssignmentEntitySet);
        when(persistenceUtil.convertEntityToRoleAssignment(roleAssignmentEntitySet.iterator().next()))
            .thenReturn(TestDataBuilder.buildRoleAssignment(LIVE));
        List<RoleAssignment> roleAssignmentList = sut.getAssignmentsByActor(id);
        assertNotNull(roleAssignmentList);

        assertFalse(roleAssignmentList.isEmpty());
        assertFalse(roleAssignmentList.contains(null));

        verify(persistenceUtil, times(1))
            .convertEntityToRoleAssignment(roleAssignmentEntitySet.iterator().next());
        verify(roleAssignmentRepository, times(1))
            .findByActorId(id);
    }

    @Test
    void getAssignmentsByActor_NPE() throws SQLException {
        String id = UUID.randomUUID().toString();
        when(roleAssignmentRepository.findByActorId(id))
            .thenReturn(null);

        Assertions.assertThrows(NullPointerException.class, () ->
            sut.getAssignmentsByActor(id)
        );

        verify(roleAssignmentRepository, times(1))
            .findByActorId(id);
    }

    @Test
    void  getAssignmentsByActorException() throws SQLException {
        doThrow(SQLException.class).when(roleAssignmentRepository).findByActorId(any());
        assertThrows(ResponseStatusException.class, () ->
            sut.getAssignmentsByActor(UUID.randomUUID().toString()));
    }

    @Test
    void getAssignmentById() throws IOException {
        UUID id = UUID.randomUUID();
        Optional<RoleAssignmentEntity> roleAssignmentOptional =
            Optional.of(TestDataBuilder.buildRoleAssignmentEntity(TestDataBuilder.buildRoleAssignment(LIVE)));
        when(roleAssignmentRepository.findById(id)).thenReturn(roleAssignmentOptional);
        List<RoleAssignment> roleAssignmentList = sut.getAssignmentById(id);
        assertNotNull(roleAssignmentList);
        verify(persistenceUtil, times(1))
            .convertEntityToRoleAssignment(any());
    }

    @Test
    void getAssignmentById_NPE() {
        UUID id = UUID.randomUUID();
        when(roleAssignmentRepository.findById(id)).thenReturn(null);
        Assertions.assertThrows(NullPointerException.class, () ->
            sut.getAssignmentById(id)
        );
    }

    @Test
    void getAssignmentById_nullRole() throws IOException {
        UUID id = UUID.randomUUID();
        Optional<RoleAssignmentEntity> roleAssignmentOptional =
            Optional.of(TestDataBuilder.buildRoleAssignmentEntity(TestDataBuilder.buildRoleAssignment(LIVE)));
        when(roleAssignmentRepository.findById(id)).thenReturn(roleAssignmentOptional);
        when(persistenceUtil.convertEntityToRoleAssignment(any())).thenReturn(null);
        List<RoleAssignment> roleAssignmentList = sut.getAssignmentById(id);
        assertNotNull(roleAssignmentList);
        verify(persistenceUtil, times(1)).convertEntityToRoleAssignment(any());
    }

    @Test
    void releaseDBChangeLock() {
        DatabaseChangelogLockEntity databaseChangelogLockEntity = DatabaseChangelogLockEntity.builder().id(1).locked(
            false).lockedby(null).build();
        when(databseChangelogLockRepository.getById(1)).thenReturn(databaseChangelogLockEntity);
        DatabaseChangelogLockEntity entity = sut.releaseDatabaseLock(1);
        verify(databseChangelogLockRepository, times(1)).releaseLock(1);
        assertFalse(entity.isLocked());

    }

    @Test
    void postRoleAssignmentsByQueryRequest() throws IOException {


        List<RoleAssignmentEntity> tasks = new ArrayList<>();
        tasks.add(TestDataBuilder.buildRoleAssignmentEntity(TestDataBuilder.buildRoleAssignment(LIVE)));

        Page<RoleAssignmentEntity> page = new PageImpl<>(tasks);


        List<String> actorId = Arrays.asList(
            "123e4567-e89b-42d3-a456-556642445678",
            "4dc7dd3c-3fb5-4611-bbde-5101a97681e1"
        );
        List<String> roleType = Arrays.asList("CASE", "ORGANISATION");

        QueryRequest queryRequest = QueryRequest.builder()
            .actorId(actorId)
            .roleType(roleType)
            .build();

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(
            Pageable.class);

        Specification<RoleAssignmentEntity> spec = Specification.where(any());
        Pageable pageableCapture = pageableCaptor.capture();

        when(roleAssignmentRepository.findAll(spec, pageableCapture
        ))
            .thenReturn(page);


        when(mockSpec.toPredicate(root, query, builder)).thenReturn(predicate);


        when(persistenceUtil.convertEntityToRoleAssignment(page.iterator().next()))
            .thenReturn(TestDataBuilder.buildRoleAssignment(LIVE));

        List<? extends Assignment> roleAssignmentList = sut.retrieveRoleAssignmentsByQueryRequest(queryRequest, 1,
                                                                                                  1, "id",
                                                                                                  "desc", false
        );
        assertNotNull(roleAssignmentList);
        assertFalse(roleAssignmentList.isEmpty());
        assertFalse(roleAssignmentList.contains(null));
        verify(persistenceUtil, times(1))
            .convertEntityToRoleAssignment(page.iterator().next());

    }

    @Test
    void postRoleAssignmentsByQueryRequestWithAllParameters() throws IOException {


        List<RoleAssignmentEntity> tasks = new ArrayList<>();
        tasks.add(TestDataBuilder.buildRoleAssignmentEntity(TestDataBuilder.buildRoleAssignment(LIVE)));

        Page<RoleAssignmentEntity> page = new PageImpl<>(tasks);


        List<String> actorId = Arrays.asList(
            "123e4567-e89b-42d3-a456-556642445678",
            "4dc7dd3c-3fb5-4611-bbde-5101a97681e1"
        );
        List<String> roleType = Arrays.asList("CASE", "ORGANISATION");
        List<String> roleNames = Arrays.asList("judge", "senior judge");
        List<String> roleCategories = Collections.singletonList("JUDICIAL");
        List<String> classifications = Arrays.asList("PUBLIC", "PRIVATE");
        Map<String, List<String>> attributes = new HashMap<>();
        List<String> regions = Arrays.asList("London", "JAPAN");
        List<String> contractTypes = Arrays.asList("SALARIED", "Non SALARIED");
        attributes.put("region", regions);
        attributes.put("contractType", contractTypes);
        List<String> grantTypes = Arrays.asList("SPECIFIC", "STANDARD");

        QueryRequest queryRequest = QueryRequest.builder()
            .actorId(actorId)
            .roleType(roleType)
            .roleCategory(roleCategories)
            .roleName(roleNames)
            .classification(classifications)
            .attributes(attributes)
            .validAt(now())
            .grantType(grantTypes)
            .build();

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(
            Pageable.class);

        Specification<RoleAssignmentEntity> spec = Specification.where(any());
        Pageable pageableCapture = pageableCaptor.capture();

        when(roleAssignmentRepository.findAll(spec, pageableCapture
        ))
            .thenReturn(page);


        when(mockSpec.toPredicate(root, query, builder)).thenReturn(predicate);


        when(persistenceUtil.convertEntityToRoleAssignment(page.iterator().next()))
            .thenReturn(TestDataBuilder.buildRoleAssignment(LIVE));

        List<Assignment> roleAssignmentList = sut.retrieveRoleAssignmentsByQueryRequest(queryRequest, 1,
                                                                                        1, "id",
                                                                                        "desc", false
        );
        assertNotNull(roleAssignmentList);

        assertNotNull(roleAssignmentList);
        assertFalse(roleAssignmentList.isEmpty());

        verify(persistenceUtil, times(1))
            .convertEntityToRoleAssignment(page.iterator().next());

    }

    @Test
    void postRoleAssignmentsByQueryRequest_ThrowsException() {

        ReflectionTestUtils.setField(sut, "defaultSize", 1);
        ReflectionTestUtils.setField(sut, "sortColumn", "id");
        List<String> actorId = Arrays.asList(
            "123e4567-e89b-42d3-a456-556642445678",
            "4dc7dd3c-3fb5-4611-bbde-5101a97681e1"
        );
        List<String> roleType = Arrays.asList("CASE", "ORGANISATION");
        QueryRequest queryRequest = QueryRequest.builder()
            .actorId(actorId)
            .roleType(roleType)
            .build();

        Specification<RoleAssignmentEntity> spec = Specification.where(any());

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(
            Pageable.class);
        Pageable pageableCapture = pageableCaptor.capture();

        when(roleAssignmentRepository.findAll(spec, pageableCapture))
            .thenThrow(ResourceNotFoundException.class);


        Assertions.assertThrows(ResourceNotFoundException.class, () ->
            sut.retrieveRoleAssignmentsByQueryRequest(queryRequest, null, null, null, null,false)
        );

    }

    @Test
    void postRoleAssignmentsByAuthorisations_ThrowsException() {

        ReflectionTestUtils.setField(sut, "defaultSize", 1);
        ReflectionTestUtils.setField(sut, "sortColumn", "id");
        List<String> authorisations = Arrays.asList(
            "dev",
            "ops"
        );

        QueryRequest queryRequest = QueryRequest.builder()
            .authorisations(authorisations)
            .build();

        Specification<RoleAssignmentEntity> spec = Specification.where(any());

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(
            Pageable.class);
        Pageable pageableCapture = pageableCaptor.capture();

        when(roleAssignmentRepository.findAll(spec, pageableCapture
        ))
            .thenThrow(ResourceNotFoundException.class);


        Assertions.assertThrows(ResourceNotFoundException.class, () ->
            sut.retrieveRoleAssignmentsByQueryRequest(queryRequest, 0, 0, "id", "desc",false)
        );

    }

    @Test
    void postRoleAssignmentsByAuthorisation() throws IOException {


        List<RoleAssignmentEntity> tasks = new ArrayList<>();
        tasks.add(TestDataBuilder.buildRoleAssignmentEntity(TestDataBuilder.buildRoleAssignment(LIVE)));

        Page<RoleAssignmentEntity> page = new PageImpl<>(tasks);


        List<String> authorisations = Arrays.asList(
            "dev",
            "tester"
        );

        QueryRequest queryRequest = QueryRequest.builder()
            .authorisations(authorisations)
            .build();

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(
            Pageable.class);

        Specification<RoleAssignmentEntity> spec = Specification.where(any());
        Pageable pageableCapture = pageableCaptor.capture();

        when(roleAssignmentRepository.findAll(spec, pageableCapture
        ))
            .thenReturn(page);


        when(mockSpec.toPredicate(root, query, builder)).thenReturn(predicate);


        when(persistenceUtil.convertEntityToRoleAssignment(page.iterator().next()))
            .thenReturn(TestDataBuilder.buildRoleAssignment(LIVE));

        List<? extends Assignment> roleAssignmentList = sut.retrieveRoleAssignmentsByQueryRequest(queryRequest, 1,
                                                                                                  1, "id",
                                                                                            "desc",false
        );
        assertNotNull(roleAssignmentList);

        assertNotNull(roleAssignmentList);
        assertFalse(roleAssignmentList.isEmpty());

        verify(persistenceUtil, times(1))
            .convertEntityToRoleAssignment(page.iterator().next());

    }

    @Test
    void shouldReturnTheTotalRecords()  {

        when(pageable.getTotalElements()).thenReturn(Long.valueOf(0));
        Long count = sut.getTotalRecords();
        assertNotNull(count);
        assertEquals(count,Long.valueOf(1));


    }

    @Test
    void shouldNotReturnTheTotalRecords()  {
        Long count = sut.getTotalRecords();
        assertNotNull(count);
        assertEquals(count,Long.valueOf(1));


    }

    @Test
    void shouldReturnEmptyListOfRoleAssignmentRecords()  {
        UUID id = UUID.randomUUID();
        Optional<RoleAssignmentEntity> roleAssignmentOptional = Optional.empty();

        when(roleAssignmentRepository.findById(id)).thenReturn(roleAssignmentOptional);
        List<RoleAssignment> roleAssignmentList = sut.getAssignmentById(id);
        assertNotNull(roleAssignmentList);
        assertEquals(roleAssignmentList.size(),Integer.valueOf(0));
    }

    @Test
    void shouldGetEmptyListForExistingFlagFalse() throws IOException {
        UUID id = UUID.randomUUID();
        AssignmentRequest assignmentRequest = TestDataBuilder
            .buildAssignmentRequest(CREATED, LIVE, false);
        RoleAssignmentEntity roleAssignmentEntity = TestDataBuilder.convertRoleAssignmentToEntity(
            assignmentRequest.getRequestedRoles().iterator().next());
        Optional<RoleAssignmentEntity> roleAssignmentOptional = Optional.of(roleAssignmentEntity);

        when(roleAssignmentRepository.findById(id)).thenReturn(roleAssignmentOptional);
        List<RoleAssignment> roleAssignmentList = sut.getAssignmentById(id);
        verify(persistenceUtil, times(1))
            .convertEntityToRoleAssignment(roleAssignmentEntity);
        assertNotNull(roleAssignmentList);
        assertNotNull(roleAssignmentOptional);
        assertFalse(roleAssignmentList.isEmpty());
        assertEquals(roleAssignmentList.size(),Integer.valueOf(1));

    }

    @Test
    void shouldGetEmptyListForExistingFlagTrue() throws IOException {
        UUID id = UUID.randomUUID();
        AssignmentRequest assignmentRequest = TestDataBuilder
            .buildAssignmentRequest(CREATED, LIVE, true);
        RoleAssignmentEntity roleAssignmentEntity = TestDataBuilder.convertRoleAssignmentToEntity(
            assignmentRequest.getRequestedRoles().iterator().next());
        Optional<RoleAssignmentEntity> roleAssignmentOptional = Optional.of(roleAssignmentEntity);

        when(roleAssignmentRepository.findById(id)).thenReturn(roleAssignmentOptional);
        List<RoleAssignment> roleAssignmentList = sut.getAssignmentById(id);
        verify(persistenceUtil, times(1))
            .convertEntityToRoleAssignment(roleAssignmentEntity);
        assertNotNull(roleAssignmentList);
        assertNotNull(roleAssignmentOptional);
        assertFalse(roleAssignmentList.isEmpty());
        assertEquals(roleAssignmentList.size(),Integer.valueOf(1));

    }

    @Test
    void postRoleAssignmentsByQueryRequestWithTrueFlag() throws IOException {


        List<RoleAssignmentEntity> tasks = new ArrayList<>();
        tasks.add(TestDataBuilder.buildRoleAssignmentEntity(TestDataBuilder.buildRoleAssignment(LIVE)));

        Page<RoleAssignmentEntity> page = new PageImpl<>(tasks);


        List<String> actorId = Arrays.asList(
            "123e4567-e89b-42d3-a456-556642445678",
            "4dc7dd3c-3fb5-4611-bbde-5101a97681e1"
        );
        List<String> roleType = Arrays.asList("CASE", "ORGANISATION");
        List<String> roleNames = Arrays.asList("judge", "senior judge");
        List<String> roleCategories = Collections.singletonList("JUDICIAL");
        List<String> classifications = Arrays.asList("PUBLIC", "PRIVATE");
        Map<String, List<String>> attributes = new HashMap<>();
        List<String> regions = Arrays.asList("London", "JAPAN");
        List<String> contractTypes = Arrays.asList("SALARIED", "Non SALARIED");
        attributes.put("region", regions);
        attributes.put("contractType", contractTypes);
        List<String> grantTypes = Arrays.asList("SPECIFIC", "STANDARD");

        QueryRequest queryRequest = QueryRequest.builder()
            .actorId(actorId)
            .roleType(roleType)
            .roleCategory(roleCategories)
            .roleName(roleNames)
            .classification(classifications)
            .attributes(attributes)
            .validAt(now())
            .grantType(grantTypes)
            .build();

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(
            Pageable.class);

        Specification<RoleAssignmentEntity> spec = Specification.where(any());
        Pageable pageableCapture = pageableCaptor.capture();

        when(roleAssignmentRepository.findAll(spec, pageableCapture
        ))
            .thenReturn(page);


        when(mockSpec.toPredicate(root, query, builder)).thenReturn(predicate);


        when(persistenceUtil.convertEntityToExistingRoleAssignment(page.iterator().next()))
            .thenReturn(TestDataBuilder.buildExistingRoleForIAC("123e4567-e89b-42d3-a456-556642445678",
                                                                "judge",
                                                                RoleCategory.JUDICIAL));

        List<Assignment> roleAssignmentList = sut.retrieveRoleAssignmentsByQueryRequest(queryRequest, 1,
                                                                                        1, "id",
                                                                                        "desc", true
        );
        assertNotNull(roleAssignmentList);

        assertNotNull(roleAssignmentList);
        assertFalse(roleAssignmentList.isEmpty());

        verify(persistenceUtil, times(1))
            .convertEntityToExistingRoleAssignment(page.iterator().next());

    }

    @Test
    void postRoleAssignmentsByQueryRequestWithTrueFlag_throwException() throws IOException {

        ReflectionTestUtils.setField(sut, "defaultSize", 1);
        ReflectionTestUtils.setField(sut, "sortColumn", "id");
        List<RoleAssignmentEntity> tasks = new ArrayList<>();
        tasks.add(TestDataBuilder.buildRoleAssignmentEntity(TestDataBuilder.buildRoleAssignment(LIVE)));

        Page<RoleAssignmentEntity> page = new PageImpl<>(tasks);


        List<String> actorId = Arrays.asList(
            "123e4567-e89b-42d3-a456-556642445678",
            "4dc7dd3c-3fb5-4611-bbde-5101a97681e1"
        );
        List<String> roleType = Arrays.asList("CASE", "ORGANISATION");
        List<String> roleNames = Arrays.asList("judge", "senior judge");
        List<String> roleCategories = Collections.singletonList("JUDICIAL");
        List<String> classifications = Arrays.asList("PUBLIC", "PRIVATE");
        Map<String, List<String>> attributes = new HashMap<>();
        List<String> regions = Arrays.asList("London", "JAPAN");
        List<String> contractTypes = Arrays.asList("SALARIED", "Non SALARIED");
        attributes.put("region", regions);
        attributes.put("contractType", contractTypes);
        List<String> grantTypes = Arrays.asList("SPECIFIC", "STANDARD");

        QueryRequest queryRequest = QueryRequest.builder()
            .actorId(actorId)
            .roleType(roleType)
            .roleCategory(roleCategories)
            .roleName(roleNames)
            .classification(classifications)
            .attributes(attributes)
            .validAt(now())
            .grantType(grantTypes)
            .build();

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(
            Pageable.class);

        Specification<RoleAssignmentEntity> spec = Specification.where(any());
        Pageable pageableCapture = pageableCaptor.capture();

        when(roleAssignmentRepository.findAll(spec, pageableCapture
        ))
            .thenReturn(page);


        when(mockSpec.toPredicate(root, query, builder)).thenReturn(predicate);


        when(persistenceUtil.convertEntityToExistingRoleAssignment(page.iterator().next()))
            .thenReturn(null);

        List<Assignment> roleAssignmentList = sut.retrieveRoleAssignmentsByQueryRequest(queryRequest, null,
                                                                                        null, null,
                                                                                        null,true
        );
        assertNotNull(roleAssignmentList);

        assertNotNull(roleAssignmentList);
        assertFalse(roleAssignmentList.isEmpty());

        verify(persistenceUtil, times(1))
            .convertEntityToExistingRoleAssignment(page.iterator().next());

    }

    @Test
    void postRoleAssignmentsByQueryRequestWithTrueFlagAndPageSizeZero_throwException() throws IOException {

        ReflectionTestUtils.setField(sut, "defaultSize", 1);
        ReflectionTestUtils.setField(sut, "sortColumn", "id");
        List<RoleAssignmentEntity> tasks = new ArrayList<>();
        tasks.add(TestDataBuilder.buildRoleAssignmentEntity(TestDataBuilder.buildRoleAssignment(LIVE)));

        Page<RoleAssignmentEntity> page = new PageImpl<>(tasks);


        List<String> actorId = Arrays.asList(
            "123e4567-e89b-42d3-a456-556642445678",
            "4dc7dd3c-3fb5-4611-bbde-5101a97681e1"
        );
        List<String> roleType = Arrays.asList("CASE", "ORGANISATION");
        List<String> roleNames = Arrays.asList("judge", "senior judge");
        List<String> roleCategories = Collections.singletonList("JUDICIAL");
        List<String> classifications = Arrays.asList("PUBLIC", "PRIVATE");
        Map<String, List<String>> attributes = new HashMap<>();
        List<String> regions = Arrays.asList("London", "JAPAN");
        List<String> contractTypes = Arrays.asList("SALARIED", "Non SALARIED");
        attributes.put("region", regions);
        attributes.put("contractType", contractTypes);
        List<String> grantTypes = Arrays.asList("SPECIFIC", "STANDARD");

        QueryRequest queryRequest = QueryRequest.builder()
            .actorId(actorId)
            .roleType(roleType)
            .roleCategory(roleCategories)
            .roleName(roleNames)
            .classification(classifications)
            .attributes(attributes)
            .validAt(now())
            .grantType(grantTypes)
            .build();

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(
            Pageable.class);

        Specification<RoleAssignmentEntity> spec = Specification.where(any());
        Pageable pageableCapture = pageableCaptor.capture();

        when(roleAssignmentRepository.findAll(spec, pageableCapture
        ))
            .thenReturn(page);


        when(mockSpec.toPredicate(root, query, builder)).thenReturn(predicate);


        when(persistenceUtil.convertEntityToExistingRoleAssignment(page.iterator().next()))
            .thenReturn(null);

        List<Assignment> roleAssignmentList = sut.retrieveRoleAssignmentsByQueryRequest(queryRequest, 0,
                                                                                        0, null,
                                                                                        null,true
        );
        assertNotNull(roleAssignmentList);

        assertNotNull(roleAssignmentList);
        assertFalse(roleAssignmentList.isEmpty());

        verify(persistenceUtil, times(1))
            .convertEntityToExistingRoleAssignment(page.iterator().next());

    }

    @Test
    void getFlagStatus() {
        String flagName = "iac_1_0";
        String env = "pr";
        FlagConfig flagConfig = FlagConfig.builder()
            .env("pr")
            .flagName("iac_1_0")
            .serviceName("iac")
            .status(Boolean.TRUE)
            .build();
        when(flagConfigRepository.findByFlagNameAndEnv(flagName, env)).thenReturn(flagConfig);
        boolean response = sut.getStatusByParam(flagName, env);
        assertTrue(response);

    }

    @Test
    void persistFlagConfig() {

        FlagConfig flagConfig = FlagConfig.builder()
            .env("pr")
            .flagName("iac_1_0")
            .serviceName("iac")
            .status(Boolean.TRUE)
            .build();
        when(flagConfigRepository.save(flagConfig)).thenReturn(flagConfig);
        FlagConfig flagConfigEntity = sut.persistFlagConfig(flagConfig);
        assertNotNull(flagConfigEntity);

    }

    @Test
    public void getFlagStatus_False() {
        String flagName = "iac_1_0";
        String env = "pr";
        FlagConfig flagConfig = FlagConfig.builder()
            .env("pr")
            .flagName("iac_1_0")
            .serviceName("iac")
            .status(Boolean.FALSE)
            .build();
        when(flagConfigRepository.findByFlagNameAndEnv(flagName, env)).thenReturn(flagConfig);
        boolean response = sut.getStatusByParam(flagName, env);
        assertFalse(response);
    }


    @Test
    void postRoleAssignmentsByOneQueryRequest() throws IOException {


        List<RoleAssignmentEntity> tasks = new ArrayList<>();
        tasks.add(TestDataBuilder.buildRoleAssignmentEntity(TestDataBuilder.buildRoleAssignment(LIVE)));

        Page<RoleAssignmentEntity> page = new PageImpl<>(tasks);


        List<String> actorId = Arrays.asList(
            "123e4567-e89b-42d3-a456-556642445678",
            "4dc7dd3c-3fb5-4611-bbde-5101a97681e1"
        );
        List<String> roleType = Arrays.asList("CASE", "ORGANISATION");

        QueryRequest queryRequest = QueryRequest.builder()
            .actorId(actorId)
            .roleType(roleType)
            .build();

        MultipleQueryRequest multipleQueryRequest =  MultipleQueryRequest.builder()
            .queryRequests(Collections.singletonList(queryRequest))
            .build();

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(
            Pageable.class);

        Specification<RoleAssignmentEntity> spec = Specification.where(any());
        Pageable pageableCapture = pageableCaptor.capture();

        when(roleAssignmentRepository.findAll(spec, pageableCapture
        ))
            .thenReturn(page);


        when(mockSpec.toPredicate(root, query, builder)).thenReturn(predicate);


        when(persistenceUtil.convertEntityToRoleAssignment(page.iterator().next()))
            .thenReturn(TestDataBuilder.buildRoleAssignment(LIVE));

        List<? extends Assignment> roleAssignmentList = sut
            .retrieveRoleAssignmentsByMultipleQueryRequest(multipleQueryRequest,
                                                                       1,
                                                                       1, "id",
                                                                       "desc",
                                                                       false
        );
        assertNotNull(roleAssignmentList);
        assertFalse(roleAssignmentList.isEmpty());
        assertFalse(roleAssignmentList.contains(null));
        verify(persistenceUtil, times(1))
            .convertEntityToRoleAssignment(page.iterator().next());

    }


    @Test
    void postRoleAssignmentsByMultipleQueryRequest() throws IOException {


        List<RoleAssignmentEntity> tasks = new ArrayList<>();
        tasks.add(TestDataBuilder.buildRoleAssignmentEntity(TestDataBuilder.buildRoleAssignment(LIVE)));

        Page<RoleAssignmentEntity> page = new PageImpl<>(tasks);


        List<String> actorId = Arrays.asList(
            "123e4567-e89b-42d3-a456-556642445678",
            "4dc7dd3c-3fb5-4611-bbde-5101a97681e1"
        );
        List<String> roleType = Arrays.asList("CASE", "ORGANISATION");

        String roleName = "senior-tribunal-caseworker";

        QueryRequest queryRequest1 = QueryRequest.builder()
            .actorId(actorId)
            .roleType(roleType)
            .build();
        QueryRequest queryRequest2 = QueryRequest.builder()
            .roleName(roleName)
            .build();

        MultipleQueryRequest multipleQueryRequest =  MultipleQueryRequest.builder()
            .queryRequests(Arrays.asList(queryRequest1,queryRequest2))
            .build();

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(
            Pageable.class);

        Specification<RoleAssignmentEntity> spec = Specification.where(any());
        Pageable pageableCapture = pageableCaptor.capture();

        when(roleAssignmentRepository.findAll(spec, pageableCapture
        ))
            .thenReturn(page);


        when(mockSpec.toPredicate(root, query, builder)).thenReturn(predicate);


        when(persistenceUtil.convertEntityToRoleAssignment(page.iterator().next()))
            .thenReturn(TestDataBuilder.buildRoleAssignment(LIVE));

        List<? extends Assignment> roleAssignmentList = sut
            .retrieveRoleAssignmentsByMultipleQueryRequest(multipleQueryRequest,
                                                                             1,
                                                                             1,
                                                                             "id",
                                                                             "desc",
                                                                             false
        );
        assertNotNull(roleAssignmentList);
        assertFalse(roleAssignmentList.isEmpty());
        assertFalse(roleAssignmentList.contains(null));
        verify(persistenceUtil, times(1))
            .convertEntityToRoleAssignment(page.iterator().next());

    }

    @Test
    void persistActorCacheException() throws IOException {
        doThrow(BatchFailedException.class).when(entityManager).flush();

        Collection<RoleAssignment> roleAssignments = TestDataBuilder.buildRequestedRoleCollection(CREATED);

        assertThrows(ResponseStatusException.class, () ->
            sut.persistActorCache(roleAssignments));
    }

    @Test
    void persistActorCacheSqlException() throws IOException, SQLException {
        doThrow(SQLException.class).when(actorCacheRepository).findByActorId(any());

        Collection<RoleAssignment> roleAssignments = TestDataBuilder.buildRequestedRoleCollection(CREATED);

        assertThrows(ResponseStatusException.class, () ->
            sut.persistActorCache(roleAssignments));
    }


}
