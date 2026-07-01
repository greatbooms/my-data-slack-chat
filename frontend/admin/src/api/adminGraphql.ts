import { GraphQLClient, type RequestOptions } from 'graphql-request';
import type { TypedDocumentNode } from '@graphql-typed-document-node/core';
import {
  AdminExternalIdentityManagementDocument,
  type AdminExternalIdentityManagementQuery,
  AdminDataSourcesDocument,
  AdminDataSourceFormOptionsDocument,
  type AdminDataSourceFormOptionsQuery,
  AdminIngestionJobsDocument,
  AdminUsersDocument,
  AdminWorkspaceManagementDocument,
  type AdminWorkspaceManagementQuery,
  type AdminWorkspaceManagementQueryVariables,
  AdminWorkspacesDocument,
  type AdminWorkspacesQuery,
  type AdminWorkspacesQueryVariables,
  CreateDataSourceDocument,
  type CreateDataSourceInput,
  type CreateDataSourceMutation,
  type CreateDataSourceMutationVariables,
  CreateExternalIdentityDocument,
  type CreateExternalIdentityInput,
  type CreateExternalIdentityMutation,
  type CreateExternalIdentityMutationVariables,
  CreateUserDocument,
  type CreateUserInput,
  type CreateUserMutation,
  type CreateUserMutationVariables,
  CreateWorkspaceDocument,
  type CreateWorkspaceInput,
  type CreateWorkspaceMutation,
  type CreateWorkspaceMutationVariables,
  DeleteExternalIdentityDocument,
  type DeleteExternalIdentityMutation,
  type DeleteExternalIdentityMutationVariables,
  type AdminDataSourcesQuery,
  type AdminIngestionJobsQuery,
  type AdminIngestionJobsQueryVariables,
  type AdminUsersQuery,
  DisableUserDocument,
  type DisableUserMutation,
  type DisableUserMutationVariables,
  type RequestDataSourceSyncMutation,
  type RequestDataSourceSyncMutationVariables,
  RequestDataSourceSyncDocument,
  ResetUserPasswordDocument,
  type ResetUserPasswordMutation,
  type ResetUserPasswordMutationVariables,
  RestoreUserDocument,
  type RestoreUserMutation,
  type RestoreUserMutationVariables,
  RestoreWorkspaceDocument,
  type RestoreWorkspaceMutation,
  type RestoreWorkspaceMutationVariables,
  SoftDeleteDataSourceDocument,
  type SoftDeleteDataSourceMutation,
  type SoftDeleteDataSourceMutationVariables,
  SoftDeleteUserDocument,
  type SoftDeleteUserMutation,
  type SoftDeleteUserMutationVariables,
  SoftDeleteWorkspaceDocument,
  type SoftDeleteWorkspaceMutation,
  type SoftDeleteWorkspaceMutationVariables,
  UpdateDataSourceDocument,
  type UpdateDataSourceInput,
  type UpdateDataSourceMutation,
  type UpdateDataSourceMutationVariables,
  UpdateExternalIdentityDocument,
  type UpdateExternalIdentityInput,
  type UpdateExternalIdentityMutation,
  type UpdateExternalIdentityMutationVariables,
  UpdateUserDocument,
  type UpdateUserInput,
  type UpdateUserMutation,
  type UpdateUserMutationVariables,
  UpdateWorkspaceDocument,
  type UpdateWorkspaceInput,
  type UpdateWorkspaceMutation,
  type UpdateWorkspaceMutationVariables,
  ViewerAndDashboardDocument,
  type ViewerAndDashboardQuery
} from '../generated/graphql';
import { isAuthenticationFailureStatus, redirectToAdminLogin } from './authRedirect';
import { clearCsrfToken, fetchCsrfToken } from './csrf';

export const adminGraphqlClient = new GraphQLClient(resolveSameOriginUrl('/admin/graphql'), {
  credentials: 'include'
});

export async function fetchViewerAndDashboard(): Promise<ViewerAndDashboardQuery> {
  return await requestAdminGraphql(ViewerAndDashboardDocument);
}

export async function fetchAdminUsers(): Promise<AdminUsersQuery> {
  return await requestAdminGraphql(AdminUsersDocument);
}

export async function fetchAdminWorkspaces(includeDeleted = true): Promise<AdminWorkspacesQuery> {
  return await requestAdminGraphql<AdminWorkspacesQuery, AdminWorkspacesQueryVariables>(
    AdminWorkspacesDocument,
    { includeDeleted }
  );
}

export async function fetchAdminWorkspaceManagement(
  includeDeleted = true
): Promise<AdminWorkspaceManagementQuery> {
  return await requestAdminGraphql<AdminWorkspaceManagementQuery, AdminWorkspaceManagementQueryVariables>(
    AdminWorkspaceManagementDocument,
    { includeDeleted }
  );
}

export async function fetchAdminExternalIdentityManagement(): Promise<AdminExternalIdentityManagementQuery> {
  return await requestAdminGraphql(AdminExternalIdentityManagementDocument);
}

export async function createAdminUser(input: CreateUserInput): Promise<CreateUserMutation> {
  return await requestAdminGraphql<CreateUserMutation, CreateUserMutationVariables>(
    CreateUserDocument,
    { input }
  );
}

export async function updateAdminUser(
  id: string,
  input: UpdateUserInput
): Promise<UpdateUserMutation> {
  return await requestAdminGraphql<UpdateUserMutation, UpdateUserMutationVariables>(
    UpdateUserDocument,
    { id, input }
  );
}

export async function disableAdminUser(id: string): Promise<DisableUserMutation> {
  return await requestAdminGraphql<DisableUserMutation, DisableUserMutationVariables>(
    DisableUserDocument,
    { id }
  );
}

export async function softDeleteAdminUser(id: string): Promise<SoftDeleteUserMutation> {
  return await requestAdminGraphql<SoftDeleteUserMutation, SoftDeleteUserMutationVariables>(
    SoftDeleteUserDocument,
    { id }
  );
}

export async function restoreAdminUser(id: string): Promise<RestoreUserMutation> {
  return await requestAdminGraphql<RestoreUserMutation, RestoreUserMutationVariables>(
    RestoreUserDocument,
    { id }
  );
}

export async function resetAdminUserPassword(
  id: string,
  password: string
): Promise<ResetUserPasswordMutation> {
  return await requestAdminGraphql<ResetUserPasswordMutation, ResetUserPasswordMutationVariables>(
    ResetUserPasswordDocument,
    { id, input: { password } }
  );
}

export async function createAdminWorkspace(
  input: CreateWorkspaceInput
): Promise<CreateWorkspaceMutation> {
  return await requestAdminGraphql<CreateWorkspaceMutation, CreateWorkspaceMutationVariables>(
    CreateWorkspaceDocument,
    { input }
  );
}

export async function updateAdminWorkspace(
  id: string,
  input: UpdateWorkspaceInput
): Promise<UpdateWorkspaceMutation> {
  return await requestAdminGraphql<UpdateWorkspaceMutation, UpdateWorkspaceMutationVariables>(
    UpdateWorkspaceDocument,
    { id, input }
  );
}

export async function softDeleteAdminWorkspace(id: string): Promise<SoftDeleteWorkspaceMutation> {
  return await requestAdminGraphql<SoftDeleteWorkspaceMutation, SoftDeleteWorkspaceMutationVariables>(
    SoftDeleteWorkspaceDocument,
    { id }
  );
}

export async function restoreAdminWorkspace(id: string): Promise<RestoreWorkspaceMutation> {
  return await requestAdminGraphql<RestoreWorkspaceMutation, RestoreWorkspaceMutationVariables>(
    RestoreWorkspaceDocument,
    { id }
  );
}

export async function createAdminExternalIdentity(
  input: CreateExternalIdentityInput
): Promise<CreateExternalIdentityMutation> {
  return await requestAdminGraphql<CreateExternalIdentityMutation, CreateExternalIdentityMutationVariables>(
    CreateExternalIdentityDocument,
    { input }
  );
}

export async function updateAdminExternalIdentity(
  id: string,
  input: UpdateExternalIdentityInput
): Promise<UpdateExternalIdentityMutation> {
  return await requestAdminGraphql<UpdateExternalIdentityMutation, UpdateExternalIdentityMutationVariables>(
    UpdateExternalIdentityDocument,
    { id, input }
  );
}

export async function deleteAdminExternalIdentity(id: string): Promise<DeleteExternalIdentityMutation> {
  return await requestAdminGraphql<DeleteExternalIdentityMutation, DeleteExternalIdentityMutationVariables>(
    DeleteExternalIdentityDocument,
    { id }
  );
}

export async function fetchAdminDataSources(): Promise<AdminDataSourcesQuery> {
  return await requestAdminGraphql(AdminDataSourcesDocument);
}

export async function fetchAdminDataSourceFormOptions(): Promise<AdminDataSourceFormOptionsQuery> {
  return await requestAdminGraphql(AdminDataSourceFormOptionsDocument);
}

export async function createAdminDataSource(
  input: CreateDataSourceInput
): Promise<CreateDataSourceMutation> {
  return await requestAdminGraphql<CreateDataSourceMutation, CreateDataSourceMutationVariables>(
    CreateDataSourceDocument,
    { input }
  );
}

export async function updateAdminDataSource(
  id: string,
  input: UpdateDataSourceInput
): Promise<UpdateDataSourceMutation> {
  return await requestAdminGraphql<UpdateDataSourceMutation, UpdateDataSourceMutationVariables>(
    UpdateDataSourceDocument,
    { id, input }
  );
}

export async function softDeleteAdminDataSource(id: string): Promise<SoftDeleteDataSourceMutation> {
  return await requestAdminGraphql<SoftDeleteDataSourceMutation, SoftDeleteDataSourceMutationVariables>(
    SoftDeleteDataSourceDocument,
    { id }
  );
}

export async function requestAdminDataSourceSync(id: string): Promise<RequestDataSourceSyncMutation> {
  return await requestAdminGraphql<RequestDataSourceSyncMutation, RequestDataSourceSyncMutationVariables>(
    RequestDataSourceSyncDocument,
    { id }
  );
}

export async function fetchAdminIngestionJobs(
  dataSourceId: string,
  first = 20
): Promise<AdminIngestionJobsQuery> {
  return await requestAdminGraphql<AdminIngestionJobsQuery, AdminIngestionJobsQueryVariables>(
    AdminIngestionJobsDocument,
    { dataSourceId, first }
  );
}

async function requestAdminGraphql<TResult, TVariables extends Record<string, unknown>>(
  document: TypedDocumentNode<TResult, TVariables>,
  variables?: TVariables
): Promise<TResult> {
  const csrf = await fetchCsrfToken();
  const requestOptions = {
    document,
    variables,
    requestHeaders: {
      [csrf.headerName]: csrf.token
    }
  } as unknown as RequestOptions<TVariables, TResult>;

  try {
    return await adminGraphqlClient.request<TResult, TVariables>(requestOptions);
  } catch (error) {
    if (isAuthenticationFailureStatus(extractResponseStatus(error))) {
      clearCsrfToken();
      redirectToAdminLogin();
    }
    throw error;
  }
}

function resolveSameOriginUrl(path: string) {
  const origin = typeof window === 'undefined' ? 'http://localhost' : window.location.origin;
  return new URL(path, origin).toString();
}

function extractResponseStatus(error: unknown): number | undefined {
  if (typeof error !== 'object' || error === null || !('response' in error)) {
    return undefined;
  }

  const response = (error as { response?: { status?: unknown } }).response;
  return typeof response?.status === 'number' ? response.status : undefined;
}
