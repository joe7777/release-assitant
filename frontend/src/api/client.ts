import axios from 'axios';

const resolveBaseUrl = () => {
  const envBaseUrl =
    import.meta.env.VITE_LLM_HOST_URL || import.meta.env.REACT_APP_LLM_HOST_URL;

  if (envBaseUrl) {
    try {
      const parsed = new URL(envBaseUrl);

      if (parsed.hostname === 'llm-host') {
        const portSuffix = parsed.port ? `:${parsed.port}` : '';
        return `${parsed.protocol}//${window.location.hostname}${portSuffix}`;
      }

      return parsed.toString();
    } catch (error) {
      // ignore parsing errors and fall back to using the browser location
    }
  }

  return `${window.location.protocol}//${window.location.hostname}:8082`;
};

const api = axios.create({
  baseURL: resolveBaseUrl()
});

export type ToolCallTrace = {
  toolName: string;
  argumentsSummary: string;
  durationMs: number;
  success: boolean;
  errorMessage?: string;
};

export type ChatRunResponse = {
  output: string;
  structuredJson?: string;
  toolCalls: ToolCallTrace[];
  toolsUsed: boolean;
};

export type Change = {
  id: string;
  type: string;
  severity: string;
  title: string;
  description?: string;
  filePath?: string;
  symbol?: string;
  workpoints: number;
};

export type Analysis = {
  id: number;
  projectName: string;
  springVersionCurrent: string;
  springVersionTarget: string;
  llmModel: string;
  dependencyScope: 'ALL' | 'SPRING_ONLY' | string;
  status: string;
  totalWorkpoints: number;
  createdAt: string;
};

export type AnalysisDetail = Analysis & {
  changes: Change[];
  effort: Record<string, number>;
};

type CreateAnalysisPayload = {
  projectGitUrl: string;
  projectName: string;
  branch: string;
  springVersionTarget: string;
  llmModel: string;
  dependencyScope: 'ALL' | 'SPRING_ONLY';
  gitTokenId?: string;
};

export async function runPrompt(prompt: string, dryRun: boolean) {
  const response = await api.post<ChatRunResponse>('/chat', { prompt, dryRun });
  return response.data;
}

export async function fetchAnalyses() {
  const response = await api.get<Analysis[]>('/analyses');
  return response.data;
}

export async function fetchAnalysis(id: string | number) {
  const response = await api.get<AnalysisDetail>(`/analyses/${id}`);
  return response.data;
}

export async function createAnalysis(payload: CreateAnalysisPayload) {
  const response = await api.post<Analysis>(`/analyses`, payload);
  return response.data;
}

export async function downloadReport(id: string | number, format: 'pdf' | 'excel') {
  const response = await api.get(`/analyses/${id}/report`, {
    params: { format },
    responseType: format === 'pdf' ? 'blob' : 'json'
  });
  return response.data;
}
