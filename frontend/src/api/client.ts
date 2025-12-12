import axios from 'axios';

const api = axios.create({
  baseURL:
    import.meta.env.REACT_APP_API_BASE_URL ||
    import.meta.env.VITE_API_BASE_URL ||
    'http://localhost:8080'
});

type AnalysisPayload = {
  projectGitUrl: string;
  projectName: string;
  branch: string;
  springVersionTarget: string;
};

export type Analysis = {
  id: string;
  projectName: string;
  springVersionCurrent: string;
  springVersionTarget: string;
  status: string;
  totalWorkpoints: number;
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
