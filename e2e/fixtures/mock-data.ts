import type { Page } from '@playwright/test';

// ========== ApplicationStats ==========

export interface ApplicationStats {
  applicationId: number;
  applicationKey: string;
  applicationName: string;
  description: string | null;
  repoUrl: string | null;
  repoProvider: string | null;
  defaultBranch: string;
  lastIndexedAt: string | null;
  lastIndexStatus: string | null;
  lastIndexError: string | null;
  lastCommitSha: string | null;
  languages: string[];
  tags: string[];
  createdAt: string;
  updatedAt: string;
  nodeCount: number;
  chunkCount: number;
  edgeCount: number;
  synonymCount: number;
}

export interface GlobalStats {
  totalApplications: number;
  totalNodes: number;
  totalChunks: number;
  totalEdges: number;
}

export interface DashboardResponse {
  global: GlobalStats;
  applications: ApplicationStats[];
}

// ========== Ingest DTOs ==========

export interface IngestStepDto {
  stepType: string;
  status: string;
  itemsProcessed: number | null;
  itemsTotal: number | null;
  errorMessage: string | null;
  startedAt: string | null;
  finishedAt: string | null;
}

export interface IngestRunDto {
  runId: number;
  applicationId: number;
  status: string;
  triggeredBy: string | null;
  branch: string | null;
  commitSha: string | null;
  errorMessage: string | null;
  steps: IngestStepDto[];
  startedAt: string | null;
  finishedAt: string | null;
  createdAt: string;
}

export interface IngestEventDto {
  eventId: number;
  runId: number;
  stepType: string | null;
  level: string;
  message: string;
  context: Record<string, unknown> | null;
  createdAt: string;
}

// ========== Mock Data ==========

export const MOCK_DASHBOARD_STATS: DashboardResponse = {
  global: {
    totalApplications: 2,
    totalNodes: 1500,
    totalChunks: 3200,
    totalEdges: 4500,
  },
  applications: [
    {
      applicationId: 1,
      applicationKey: 'my-service',
      applicationName: 'My Service',
      description: 'Main backend service',
      repoUrl: 'https://github.com/org/my-service',
      repoProvider: 'GITHUB',
      defaultBranch: 'main',
      lastIndexedAt: '2025-06-01T12:00:00Z',
      lastIndexStatus: 'success',
      lastIndexError: null,
      lastCommitSha: 'abc12345deadbeef',
      languages: ['Kotlin', 'Java'],
      tags: ['backend'],
      createdAt: '2025-01-01T00:00:00Z',
      updatedAt: '2025-06-01T12:00:00Z',
      nodeCount: 800,
      chunkCount: 1600,
      edgeCount: 2200,
      synonymCount: 50,
    },
    {
      applicationId: 2,
      applicationKey: 'frontend-app',
      applicationName: 'Frontend App',
      description: 'React frontend',
      repoUrl: 'https://gitlab.com/org/frontend-app',
      repoProvider: 'GITLAB',
      defaultBranch: 'develop',
      lastIndexedAt: '2025-05-20T09:30:00Z',
      lastIndexStatus: 'failed',
      lastIndexError: 'Build graph timeout',
      lastCommitSha: 'ff00ff00aabbccdd',
      languages: ['TypeScript', 'JavaScript'],
      tags: ['frontend'],
      createdAt: '2025-02-01T00:00:00Z',
      updatedAt: '2025-05-20T09:30:00Z',
      nodeCount: 700,
      chunkCount: 1600,
      edgeCount: 2300,
      synonymCount: 30,
    },
  ],
};

export const MOCK_EMPTY_DASHBOARD: DashboardResponse = {
  global: { totalApplications: 0, totalNodes: 0, totalChunks: 0, totalEdges: 0 },
  applications: [],
};

function pendingStep(stepType: string): IngestStepDto {
  return {
    stepType,
    status: 'PENDING',
    itemsProcessed: null,
    itemsTotal: null,
    errorMessage: null,
    startedAt: null,
    finishedAt: null,
  };
}

function completedStep(stepType: string, durationMs: number = 5000): IngestStepDto {
  const started = '2025-06-02T10:00:00Z';
  const finished = new Date(new Date(started).getTime() + durationMs).toISOString();
  return {
    stepType,
    status: 'COMPLETED',
    itemsProcessed: 100,
    itemsTotal: 100,
    errorMessage: null,
    startedAt: started,
    finishedAt: finished,
  };
}

export const STEP_ORDER = ['CHECKOUT', 'RESOLVE_CLASSPATH', 'BUILD_LIBRARY', 'BUILD_GRAPH', 'LINK'];

export const MOCK_INGEST_RUN_STARTED: IngestRunDto = {
  runId: 42,
  applicationId: 1,
  status: 'PENDING',
  triggeredBy: 'ui',
  branch: 'main',
  commitSha: 'abc12345deadbeef',
  errorMessage: null,
  steps: STEP_ORDER.map(pendingStep),
  startedAt: '2025-06-02T10:00:00Z',
  finishedAt: null,
  createdAt: '2025-06-02T10:00:00Z',
};

export const MOCK_COMPLETED_RUN: IngestRunDto = {
  runId: 41,
  applicationId: 1,
  status: 'COMPLETED',
  triggeredBy: 'ui',
  branch: 'main',
  commitSha: 'abc12345deadbeef',
  errorMessage: null,
  steps: STEP_ORDER.map(s => completedStep(s)),
  startedAt: '2025-06-02T10:00:00Z',
  finishedAt: '2025-06-02T10:05:00Z',
  createdAt: '2025-06-02T10:00:00Z',
};

export const MOCK_FAILED_RUN: IngestRunDto = {
  runId: 40,
  applicationId: 1,
  status: 'FAILED',
  triggeredBy: 'ui',
  branch: 'main',
  commitSha: 'abc12345deadbeef',
  errorMessage: 'Build graph failed: timeout',
  steps: [
    completedStep('CHECKOUT', 2000),
    completedStep('RESOLVE_CLASSPATH', 3000),
    completedStep('BUILD_LIBRARY', 4000),
    {
      stepType: 'BUILD_GRAPH',
      status: 'FAILED',
      itemsProcessed: 50,
      itemsTotal: 100,
      errorMessage: 'timeout',
      startedAt: '2025-06-02T10:00:09Z',
      finishedAt: '2025-06-02T10:01:09Z',
    },
    {
      stepType: 'LINK',
      status: 'SKIPPED',
      itemsProcessed: null,
      itemsTotal: null,
      errorMessage: null,
      startedAt: null,
      finishedAt: null,
    },
  ],
  startedAt: '2025-06-02T10:00:00Z',
  finishedAt: '2025-06-02T10:01:09Z',
  createdAt: '2025-06-02T10:00:00Z',
};

export const MOCK_HISTORY_RUNS: IngestRunDto[] = [
  MOCK_COMPLETED_RUN,
  MOCK_FAILED_RUN,
  {
    runId: 39,
    applicationId: 1,
    status: 'COMPLETED',
    triggeredBy: 'scheduler',
    branch: 'develop',
    commitSha: '1111222233334444',
    errorMessage: null,
    steps: STEP_ORDER.map(s => completedStep(s)),
    startedAt: '2025-06-01T08:00:00Z',
    finishedAt: '2025-06-01T08:03:00Z',
    createdAt: '2025-06-01T08:00:00Z',
  },
];

// ========== SSE Helpers ==========

export function buildSseEvent(eventType: string, data: IngestEventDto): string {
  return `event: ${eventType}\ndata: ${JSON.stringify(data)}\n\n`;
}

export function buildSseStream(events: { type: string; data: IngestEventDto }[]): string {
  return events.map(e => buildSseEvent(e.type, e.data)).join('');
}

export const MOCK_SSE_EVENTS: { type: string; data: IngestEventDto }[] = [
  {
    type: 'step_started',
    data: {
      eventId: 1, runId: 42, stepType: 'CHECKOUT', level: 'INFO',
      message: 'Starting checkout...', context: null, createdAt: '2025-06-02T10:00:01Z',
    },
  },
  {
    type: 'log',
    data: {
      eventId: 2, runId: 42, stepType: 'CHECKOUT', level: 'INFO',
      message: 'Cloning repository...', context: null, createdAt: '2025-06-02T10:00:02Z',
    },
  },
  {
    type: 'step_completed',
    data: {
      eventId: 3, runId: 42, stepType: 'CHECKOUT', level: 'INFO',
      message: 'Checkout completed', context: null, createdAt: '2025-06-02T10:00:05Z',
    },
  },
  {
    type: 'step_started',
    data: {
      eventId: 4, runId: 42, stepType: 'RESOLVE_CLASSPATH', level: 'INFO',
      message: 'Resolving classpath...', context: null, createdAt: '2025-06-02T10:00:06Z',
    },
  },
  {
    type: 'log',
    data: {
      eventId: 5, runId: 42, stepType: 'RESOLVE_CLASSPATH', level: 'WARN',
      message: 'Some dependencies unresolved', context: null, createdAt: '2025-06-02T10:00:07Z',
    },
  },
  {
    type: 'step_completed',
    data: {
      eventId: 6, runId: 42, stepType: 'RESOLVE_CLASSPATH', level: 'INFO',
      message: 'Classpath resolved', context: null, createdAt: '2025-06-02T10:00:10Z',
    },
  },
];

// ========== Shared Mock Setup ==========

/** Sets up the default GET /api/dashboard/stats mock. Call before page.goto(). */
export async function setupDefaultMocks(page: Page, stats: DashboardResponse = MOCK_DASHBOARD_STATS) {
  await page.route('**/api/dashboard/stats', (route) => {
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(stats),
    });
  });
}
