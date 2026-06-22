import { GraphQLClient, type RequestOptions } from 'graphql-request';
import type { TypedDocumentNode } from '@graphql-typed-document-node/core';
import {
  AdminDataSourcesDocument,
  AdminIngestionJobsDocument,
  AdminUsersDocument,
  CreateDataSourceDocument,
  type CreateDataSourceInput,
  type CreateDataSourceMutation,
  type CreateDataSourceMutationVariables,
  CreateUserDocument,
  type CreateUserInput,
  type CreateUserMutation,
  type CreateUserMutationVariables,
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
  SoftDeleteDataSourceDocument,
  type SoftDeleteDataSourceMutation,
  type SoftDeleteDataSourceMutationVariables,
  SoftDeleteUserDocument,
  type SoftDeleteUserMutation,
  type SoftDeleteUserMutationVariables,
  UpdateDataSourceDocument,
  type UpdateDataSourceInput,
  type UpdateDataSourceMutation,
  type UpdateDataSourceMutationVariables,
  UpdateUserDocument,
  type UpdateUserInput,
  type UpdateUserMutation,
  type UpdateUserMutationVariables,
  ViewerAndDashboardDocument,
  type ViewerAndDashboardQuery
} from '../generated/graphql';
import { fetchCsrfToken } from './csrf';

export const adminGraphqlClient = new GraphQLClient(resolveSameOriginUrl('/admin/graphql'), {
  credentials: 'include'
});

export async function fetchViewerAndDashboard(): Promise<ViewerAndDashboardQuery> {
  return await requestAdminGraphql(ViewerAndDashboardDocument);
}

export async function fetchAdminUsers(): Promise<AdminUsersQuery> {
  return await requestAdminGraphql(AdminUsersDocument);
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

export async function fetchAdminDataSources(): Promise<AdminDataSourcesQuery> {
  return await requestAdminGraphql(AdminDataSourcesDocument);
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

  return await adminGraphqlClient.request<TResult, TVariables>(requestOptions);
}

function resolveSameOriginUrl(path: string) {
  const origin = typeof window === 'undefined' ? 'http://localhost' : window.location.origin;
  return new URL(path, origin).toString();
}
