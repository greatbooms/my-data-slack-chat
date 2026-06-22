import { GraphQLClient } from 'graphql-request';
import { AdminShellDocument, type AdminShellQuery } from '../generated/graphql';

export const adminGraphqlClient = new GraphQLClient('/admin/graphql', {
  credentials: 'include'
});

export function fetchAdminShell(): Promise<AdminShellQuery> {
  return adminGraphqlClient.request(AdminShellDocument);
}
