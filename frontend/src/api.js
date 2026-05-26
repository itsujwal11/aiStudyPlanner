import axios from 'axios'

const API_BASE_URL = 'http://localhost:9096/api'

const api = axios.create({
  baseURL: API_BASE_URL,
  headers: {
    'Content-Type': 'application/json',
  },
})

api.interceptors.request.use((config) => {
  const token = localStorage.getItem('token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

api.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      localStorage.removeItem('token')
      localStorage.removeItem('user')
      window.location.href = '/login'
    }
    return Promise.reject(error)
  }
)

export const authAPI = {
  register: (data) => api.post('/auth/register', data),
  login: (data) => api.post('/auth/login', data),
}

export const pdfAPI = {
  upload: (file, examDate) => {
    const formData = new FormData()
    formData.append('file', file)
    formData.append('examDate', examDate)
    return api.post('/pdfs/upload', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    })
  },
  list: () => api.get('/pdfs'),
  delete: (pdfId) => api.delete(`/pdfs/${pdfId}`),
  reset: () => api.delete('/pdfs/reset'),
}

export const topicAPI = {
  analyze: (pdfId) => api.post(`/topics/analyze/${pdfId}`),
  getByPdf: (pdfId) => api.get(`/topics/pdf/${pdfId}`),
  getRanked: () => api.get('/topics/ranked'),
  getById: (topicId) => api.get(`/topics/${topicId}`),
  updateWeakness: (topicId, data) => api.post(`/topics/${topicId}/update-weakness`, data),
}

export const quizAPI = {
  getByTopic: (topicId) => api.get(`/quizzes/topic/${topicId}`),
  getById: (quizId) => api.get(`/quizzes/${quizId}`),
  submit: (quizId, data) => api.post(`/quizzes/${quizId}/submit`, data),
}

export const dashboardAPI = {
  get: () => api.get('/dashboard'),
}

export const analyticsAPI = {
  getPerformance: () => api.get('/analytics/performance'),
  getTopicAnalytics: (topicId) => api.get(`/analytics/topic/${topicId}`),
  getComparison: () => api.get('/analytics/comparison'),
}

export const recommendationAPI = {
  // existing functions...
  getNextTopics: (limit = 5) => api.get(`/recommendations/next-topics?limit=${limit}`),
  getInsights: () => api.get('/recommendations/insights'),
  getSchedule: (daysAhead = 7) => api.get(`/recommendations/schedule?daysAhead=${daysAhead}`),
};

export const plannerAPI = {
  get: () => api.get('/planner'),
};

export const studyPlanAPI = {
  generate: (payload) => api.post('/study-plan/generate', payload),
};

export const reportAPI = {
  generateStudyReport: () => api.get('/reports/study-report'),
};

export default api;
