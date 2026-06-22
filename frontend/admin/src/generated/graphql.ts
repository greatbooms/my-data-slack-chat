/* eslint-disable */
/** Internal type. DO NOT USE DIRECTLY. */
type Exact<T extends { [key: string]: unknown }> = { [K in keyof T]: T[K] };
/** Internal type. DO NOT USE DIRECTLY. */
export type Incremental<T> = T | { [P in keyof T]?: P extends ' $fragmentName' | '__typename' ? T[P] : never };
import type { TypedDocumentNode as DocumentNode } from '@graphql-typed-document-node/core';
export type DataSourceStatus =
  | 'ACTIVE'
  | 'ERROR'
  | 'PAUSED';

export type DataSourceType =
  | 'GOOGLE_DRIVE'
  | 'LOCAL_TEXT'
  | 'NOTION'
  | 'SLACK';

export type DataSourceVisibility =
  | 'PRIVATE'
  | 'WORKSPACE';

export type SyncMode =
  | 'MANUAL'
  | 'MANUAL_AND_SCHEDULED'
  | 'SCHEDULED';

export type UserRole =
  | 'ADMIN'
  | 'USER';

export type UserStatus =
  | 'ACTIVE'
  | 'DISABLED';

export type AdminShellQueryVariables = Exact<{ [key: string]: never; }>;


export type AdminShellQuery = { viewer: { id: string, email: string, displayName: string, role: UserRole }, dashboardSummary: { userCount: number, dataSourceCount: number, runningJobCount: number } };

export type AdminDataSourcesQueryVariables = Exact<{ [key: string]: never; }>;


export type AdminDataSourcesQuery = { dataSources: { totalCount: number, items: Array<{ id: string, workspaceId: string, ownerUserId: string | null, type: DataSourceType, name: string, status: DataSourceStatus, syncMode: SyncMode, visibility: DataSourceVisibility, lastSyncedAt: string | null, deletedAt: string | null }> } };

export type AdminUsersQueryVariables = Exact<{ [key: string]: never; }>;


export type AdminUsersQuery = { users: { totalCount: number, items: Array<{ id: string, email: string, displayName: string, role: UserRole, status: UserStatus, deletedAt: string | null }> } };


export const AdminShellDocument = {"kind":"Document","definitions":[{"kind":"OperationDefinition","operation":"query","name":{"kind":"Name","value":"AdminShell"},"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"viewer"},"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"id"}},{"kind":"Field","name":{"kind":"Name","value":"email"}},{"kind":"Field","name":{"kind":"Name","value":"displayName"}},{"kind":"Field","name":{"kind":"Name","value":"role"}}]}},{"kind":"Field","name":{"kind":"Name","value":"dashboardSummary"},"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"userCount"}},{"kind":"Field","name":{"kind":"Name","value":"dataSourceCount"}},{"kind":"Field","name":{"kind":"Name","value":"runningJobCount"}}]}}]}}]} as unknown as DocumentNode<AdminShellQuery, AdminShellQueryVariables>;
export const AdminDataSourcesDocument = {"kind":"Document","definitions":[{"kind":"OperationDefinition","operation":"query","name":{"kind":"Name","value":"AdminDataSources"},"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"dataSources"},"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"totalCount"}},{"kind":"Field","name":{"kind":"Name","value":"items"},"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"id"}},{"kind":"Field","name":{"kind":"Name","value":"workspaceId"}},{"kind":"Field","name":{"kind":"Name","value":"ownerUserId"}},{"kind":"Field","name":{"kind":"Name","value":"type"}},{"kind":"Field","name":{"kind":"Name","value":"name"}},{"kind":"Field","name":{"kind":"Name","value":"status"}},{"kind":"Field","name":{"kind":"Name","value":"syncMode"}},{"kind":"Field","name":{"kind":"Name","value":"visibility"}},{"kind":"Field","name":{"kind":"Name","value":"lastSyncedAt"}},{"kind":"Field","name":{"kind":"Name","value":"deletedAt"}}]}}]}}]}}]} as unknown as DocumentNode<AdminDataSourcesQuery, AdminDataSourcesQueryVariables>;
export const AdminUsersDocument = {"kind":"Document","definitions":[{"kind":"OperationDefinition","operation":"query","name":{"kind":"Name","value":"AdminUsers"},"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"users"},"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"totalCount"}},{"kind":"Field","name":{"kind":"Name","value":"items"},"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"id"}},{"kind":"Field","name":{"kind":"Name","value":"email"}},{"kind":"Field","name":{"kind":"Name","value":"displayName"}},{"kind":"Field","name":{"kind":"Name","value":"role"}},{"kind":"Field","name":{"kind":"Name","value":"status"}},{"kind":"Field","name":{"kind":"Name","value":"deletedAt"}}]}}]}}]}}]} as unknown as DocumentNode<AdminUsersQuery, AdminUsersQueryVariables>;