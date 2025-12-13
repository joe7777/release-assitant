import axios from 'axios';

const resolveBaseUrl = () => {
  const envBaseUrl =
    import.meta.env.REACT_APP_API_BASE_URL || import.meta.env.VITE_API_BASE_URL;

  if (envBaseUrl) {
    try {
      const parsed = new URL(envBaseUrl);

      // When built via docker-compose, VITE_API_BASE_URL defaults to
      // "http://backend:8080", which is only resolvable from inside the
      // container network. Replace that hostname with the one serving the UI
      // so browser requests succeed.
      if (parsed.hostname === 'backend') {
        const portSuffix = parsed.port ? `:${parsed.port}` : '';
        return `${parsed.protocol}//${window.location.hostname}${portSuffix}`;
      }

      return parsed.toString();
    } catch (error) {
      // ignore parsing errors and fall back to using the browser location
    }
  }

  return `${window.location.protocol}//${window.location.hostname}:8080`;
};

const api = axios.create({
  baseURL: resolveBaseUrl()
});

type AnalysisPayload = {
  projectGitUrl: string;
  projectName: string;
  branch: string;
  springVersionTarget: string;
  llmModel: string;
  dependencyScope: 'ALL' | 'SPRING_ONLY';
};

export type Analysis = {
  id: string;
  projectName: string;
  springVersionCurrent: string;
  springVersionTarget: string;
  llmModel?: string;
  status: string;
  totalWorkpoints: number;
  dependencyScope?: 'ALL' | 'SPRING_ONLY';
};

export type AnalysisDetail = Analysis & {
  changes: Array<{
    type: string;
    severity: string;
    title: string;
    workpoints: number;
  }>;
};

export async function createAnalysis(payload: AnalysisPayload) {
  const response = await api.post<Analysis>('/analyses', payload);
  return response.data;
}

export async function fetchAnalyses() {
  const response = await api.get<Analysis[]>('/analyses');
  return response.data;
}

export async function fetchAnalysis(id: string) {
  const response = await api.get<AnalysisDetail>(`/analyses/${id}`);
  return response.data;
}

export async function downloadReport(id: string, format: 'pdf' | 'excel') {
  const response = await api.get(`/analyses/${id}/report`, {
    params: { format },
    responseType: format === 'pdf' ? 'blob' : 'json'
  });

  return response.data;
}
