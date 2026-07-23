import axios from "axios";

const API_BASE_URL = import.meta.env.VITE_API_URL || "http://localhost:8080";

const api = axios.create({
  baseURL: API_BASE_URL,
  timeout: 120000,
  headers: {
    "Content-Type": "application/json",
  },
});

export const analyzeRepository = async (repositoryUrl) => {
  const response = await api.post("/api/analyze", { repositoryUrl });
  const data = response.data;
  
  if (data && data.jobId) {
    const jobId = data.jobId;
    return new Promise((resolve, reject) => {
      const interval = setInterval(async () => {
        try {
          const statusRes = await api.get(`/api/analyze/${jobId}/status`);
          const statusData = statusRes.data;
          
          if (statusData.status === "COMPLETED" || statusData.repository || statusData.overall) {
            clearInterval(interval);
            resolve(statusData);
          } else if (statusData.status?.startsWith("FAILED")) {
            clearInterval(interval);
            reject(new Error(statusData.status));
          }
          // if PENDING, just keep polling
        } catch (err) {
          clearInterval(interval);
          reject(err);
        }
      }, 3000);
    });
  }
  
  return data;
};

export const getAllAnalyses = async () => {
  const response = await api.get("/api/analyses");
  return response.data;
};

export const getAnalysisById = async (id) => {
  const response = await api.get(`/api/analyses/${id}`);
  return response.data;
};

export const deleteAnalysis = async (id) => {
  const response = await api.delete(`/api/analyses/${id}`);
  return response.data;
};

export const getDashboard = async () => {
  const response = await api.get("/api/analyses/dashboard");
  return response.data;
};

export const getRateLimit = async () => {
  const response = await api.get("/api/github/rate-limit");
  return response.data;
};

export const getPdfReportUrl = (owner, repositoryName) => {
  return `${API_BASE_URL}/api/analyses/${owner}/${repositoryName}/report/pdf`;
};

export const getErrorMessage = (error) => {
  if (error.response?.data?.message) {
    return error.response.data.message;
  }
  if (error.code === "ECONNABORTED" || error.message?.includes("timeout")) {
    return "Request timed out. The repository may be too large or GitHub API is slow. Please try again.";
  }
  if (error.code === "ERR_NETWORK") {
    return "Unable to connect to RepoDoctor API. Make sure the backend server is running.";
  }
  return error.message || "An unexpected error occurred. Please try again.";
};

export default api;
