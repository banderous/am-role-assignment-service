@F-006
Feature: Get Role Assignments by Query Params

  Background:
    Given an appropriate test context as detailed in the test data source

#  @S-061
#  Scenario: must successfully receive Role Assignments by Role Type Case and Actor Id
#    Given a user with [an active IDAM profile with full permissions],
#    And a successful call [to create a role assignment for an actor] as in [S-061_CreationDataForRoleAssignment],
#    When a request is prepared with appropriate values,
#    And the request [contains an Actor Id having only single Role Assignment],
#    And the request [contains Role Type Case and Actor Id which are assigned to Role Assignments],
#    And it is submitted to call the [Get Role Assignments by Query Params] operation of [Role Assignment Service],
#    Then a positive response is received,
#    And the response has all other details as expected.
#
#  @S-062
#  Scenario: must successfully receive Role Assignments by Role Type Case and Case Id
#    Given a user with [an active IDAM profile with full permissions],
#    And a successful call [to create a role assignment for an actor] as in [S-062_CreationDataForRoleAssignment],
#    When a request is prepared with appropriate values,
#    And the request [contains an Actor Id having only single Role Assignment],
#    And the request [contains Role Type Case and Case Id which are assigned to Role Assignments],
#    And it is submitted to call the [Get Role Assignments by Query Params] operation of [Role Assignment Service],
#    Then a positive response is received,
#    And the response has all other details as expected.
#
#  @S-063
#  Scenario: must receive an error response for Role Type other than Case
#    Given a user with [an active IDAM profile with full permissions],
#    When a request is prepared with appropriate values,
#    And the request [contains Role Type other than Case],
#    And it is submitted to call the [Get Role Assignments by Query Params] operation of [Role Assignment Service],
#    Then a negative response is received,
#    And the response has all other details as expected.
#
#  @S-064
#  Scenario: must successfully receive multiple Role Assignments by Role Type Case and Actor Id
#    Given a user with [an active IDAM profile with full permissions],
#    And a successful call [to create a role assignment for an actor] as in [S-064_CreationDataForRoleAssignment],
#    When a request is prepared with appropriate values,
#    And the request [contains multiple Role Assignments],
#    And the request [contains Role Type Case and Actor Id which are assigned to Role Assignments],
#    And it is submitted to call the [Get Role Assignments by Query Params] operation of [Role Assignment Service],
#    Then a positive response is received,
#    And the response has all other details as expected.
#
#  @S-065
#  Scenario: must successfully receive multiple Role Assignments by Role Type Case and Case Id
#    Given a user with [an active IDAM profile with full permissions],
#    And a successful call [to create a role assignment for an actor] as in [S-065_CreationDataForRoleAssignment],
#    When a request is prepared with appropriate values,
#    And the request [contains multiple Role Assignments],
#    And the request [contains Role Type Case and Id which are assigned to Role Assignments],
#    And it is submitted to call the [Get Role Assignments by Query Params] operation of [Role Assignment Service],
#    Then a positive response is received,
#    And the response has all other details as expected.
#
#  @S-066
#  Scenario: must receive an error response for non-existing Actor Id
#    Given a user with [an active IDAM profile with full permissions],
#    When a request is prepared with appropriate values,
#    And the request [contains a non-existing Actor Id],
#    And it is submitted to call the [Get Role Assignments by Query Params] operation of [Role Assignment Service],
#    Then a negative response is received,
#    And the response has all other details as expected.
#
#  @S-067
#  Scenario: must receive an error response for non-existing Case Id
#    Given a user with [an active IDAM profile with full permissions],
#    When a request is prepared with appropriate values,
#    And the request [contains a non-existing Case Id],
#    And it is submitted to call the [Get Role Assignments by Query Params] operation of [Role Assignment Service],
#    Then a negative response is received,
#    And the response has all other details as expected.
#
#  @S-068
#  Scenario: must successfully receive Role Assignments without X-Correlation-ID Header
#    Given a user with [an active IDAM profile with full permissions],
#    And a successful call [to create a role assignment for an actor] as in [S-068_CreationDataForRoleAssignment],
#    When a request is prepared with appropriate values,
#    And the request [does not have X-Correlation-ID header],
#    And it is submitted to call the [Get Role Assignments by Query Params] operation of [Role Assignment Service],
#    Then a positive response is received,
#    And the response has all other details as expected.
#
#  @S-069
#  Scenario: must successfully receive Role Assignments by both Actor Id and Case Id
#    Given a user with [an active IDAM profile with full permissions],
#    And a successful call [to create a role assignment for an actor] as in [S-069_CreationDataForRoleAssignment],
#    When a request is prepared with appropriate values,
#    And the request [contains both Actor Id and Case Id which are assigned to Role Assignments],
#    And it is submitted to call the [Get Role Assignments by Query Params] operation of [Role Assignment Service],
#    Then a positive response is received,
#    And the response has all other details as expected.
