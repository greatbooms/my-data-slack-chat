import { GraphQLClient } from 'graphql-request';
import {
  ViewerAndDashboardDocument,
  type ViewerAndDashboardQuery
} from '../generated/graphql';
import { fetchCsrfToken } from './csrf';

export const adminGraphqlClient = new GraphQLClient(resolveSameOriginUrl('/admin/graphql'), {
  credentials: 'include'
});

export async function fetchViewerAndDashboard(): Promise<ViewerAndDashboardQuery> {
  const csrf = await fetchCsrfToken();
  return await adminGraphqlClient.request({
    document: ViewerAndDashboardDocument,
    requestHeaders: {
      [csrf.headerName]: csrf.token
    }
  });
}

function resolveSameOriginUrl(path: string) {
  const origin = typeof window === 'undefined' ? 'http://localhost' : window.location.origin;
  return new URL(path, origin).toString();
}
