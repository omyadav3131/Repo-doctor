import { useState, useEffect, useCallback } from "react";
import { getAllAnalyses, getDashboard, deleteAnalysis, getPdfReportUrl, getErrorMessage } from "../api.js";

function HistoryPage() {
  const [analyses, setAnalyses] = useState([]);
  const [dashboard, setDashboard] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [searchQuery, setSearchQuery] = useState("");
  const [sortOrder, setSortOrder] = useState("desc");
  const [expandedId, setExpandedId] = useState(null);
  const [deleteConfirm, setDeleteConfirm] = useState(null);

  const loadData = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      const [analysesData, dashboardData] = await Promise.all([
        getAllAnalyses(),
        getDashboard(),
      ]);
      setAnalyses(analysesData || []);
      setDashboard(dashboardData);
    } catch (err) {
      setError(getErrorMessage(err));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadData();
  }, [loadData]);

  const handleDelete = async (id) => {
    try {
      await deleteAnalysis(id);
      setDeleteConfirm(null);
      loadData();
    } catch (err) {
      setError(getErrorMessage(err));
    }
  };

  const handleDownloadPdf = (analysis) => {
    const url = getPdfReportUrl(analysis.owner, analysis.repositoryName);
    window.open(url, "_blank");
  };

  const formatDate = (value) => {
    if (!value) return "—";
    const date = new Date(value);
    return isNaN(date.getTime()) ? String(value) : date.toLocaleDateString("en-US", {
      year: "numeric",
      month: "short",
      day: "numeric",
      hour: "2-digit",
      minute: "2-digit",
    });
  };

  const formatLabel = (value) => {
    if (!value) return "Unknown";
    return String(value).replaceAll("_", " ").replace(/\b\w/g, (c) => c.toUpperCase());
  };

  // Filter by search
  const filtered = analyses.filter((a) => {
    const query = searchQuery.toLowerCase();
    if (!query) return true;
    const name = (a.repositoryName || "").toLowerCase();
    const owner = (a.owner || "").toLowerCase();
    return name.includes(query) || owner.includes(query) || `${owner}/${name}`.includes(query);
  });

  // Sort by date
  const sorted = [...filtered].sort((a, b) => {
    const dateA = new Date(a.analysisDate || 0).getTime();
    const dateB = new Date(b.analysisDate || 0).getTime();
    return sortOrder === "desc" ? dateB - dateA : dateA - dateB;
  });

  return (
    <>
      <section className="hero history-hero">
        <h2>Analysis History</h2>
        <p>View, download, and manage your past repository analyses.</p>
      </section>

      {/* Dashboard */}
      {dashboard && (
        <section className="dashboard-section">
          <div className="section-header">
            <span className="section-label">DASHBOARD</span>
            <h3>Overview</h3>
          </div>
          <div className="overview-grid">
            <div className="metric-card">
              <span>Total Analyses</span>
              <strong>{dashboard.totalAnalyses || 0}</strong>
            </div>
            <div className="metric-card">
              <span>Recent Analysis</span>
              <strong>
                {dashboard.recentAnalysis
                  ? `${dashboard.recentAnalysis.owner}/${dashboard.recentAnalysis.repositoryName}`
                  : "None"}
              </strong>
            </div>
          </div>
        </section>
      )}

      {/* Error */}
      {error && (
        <div className="error-message" role="alert">
          <strong>Error:</strong> {error}
        </div>
      )}

      {/* Controls */}
      <section className="history-controls">
        <div className="search-box">
          <input
            type="text"
            placeholder="Search by repository name or owner..."
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
          />
        </div>
        <div className="sort-controls">
          <button
            type="button"
            className={`sort-btn ${sortOrder === "desc" ? "active" : ""}`}
            onClick={() => setSortOrder("desc")}
          >
            Newest First
          </button>
          <button
            type="button"
            className={`sort-btn ${sortOrder === "asc" ? "active" : ""}`}
            onClick={() => setSortOrder("asc")}
          >
            Oldest First
          </button>
          <button type="button" className="sort-btn" onClick={loadData}>
            ↻ Refresh
          </button>
        </div>
      </section>

      {/* History Table */}
      <section className="history-section">
        {loading ? (
          <div className="loading-placeholder">
            <span className="spinner" /> Loading analysis history...
          </div>
        ) : sorted.length === 0 ? (
          <div className="empty-state-card">
            <h4>{searchQuery ? "No matching analyses found" : "No analyses yet"}</h4>
            <p>{searchQuery
              ? "Try a different search query."
              : "Go to the Analyze page to analyze your first repository."}</p>
          </div>
        ) : (
          <div className="table-wrapper">
            <table className="history-table">
              <thead>
                <tr>
                  <th>Repository</th>
                  <th>Score</th>
                  <th>Grade</th>
                  <th>Date</th>
                  <th>Actions</th>
                </tr>
              </thead>
              <tbody>
                {sorted.map((item) => (
                  <HistoryRow
                    key={item.id}
                    item={item}
                    formatDate={formatDate}
                    formatLabel={formatLabel}
                    isExpanded={expandedId === item.id}
                    onToggleExpand={() => setExpandedId(expandedId === item.id ? null : item.id)}
                    onDownloadPdf={() => handleDownloadPdf(item)}
                    onDelete={() => setDeleteConfirm(item.id)}
                  />
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>

      {/* Delete Confirmation Modal */}
      {deleteConfirm && (
        <div className="modal-overlay" onClick={() => setDeleteConfirm(null)}>
          <div className="modal-card" onClick={(e) => e.stopPropagation()}>
            <h4>Delete Analysis</h4>
            <p>Are you sure you want to delete this analysis? This action cannot be undone.</p>
            <div className="modal-actions">
              <button type="button" className="secondary-button" onClick={() => setDeleteConfirm(null)}>
                Cancel
              </button>
              <button type="button" className="danger-button" onClick={() => handleDelete(deleteConfirm)}>
                Delete
              </button>
            </div>
          </div>
        </div>
      )}
    </>
  );
}

function HistoryRow({ item, formatDate, formatLabel, isExpanded, onToggleExpand, onDownloadPdf, onDelete }) {
  return (
    <>
      <tr className={isExpanded ? "expanded-row" : ""}>
        <td>
          <div className="repo-cell">
            <strong>{item.owner}/{item.repositoryName}</strong>
            <span className="health-tag">{formatLabel(item.repositoryHealth)}</span>
          </div>
        </td>
        <td>
          <span className="score-badge">{item.overallScore ?? "—"}</span>
        </td>
        <td>
          <span className="grade-badge">{item.grade || "—"}</span>
        </td>
        <td>{formatDate(item.analysisDate)}</td>
        <td>
          <div className="action-buttons">
            <button type="button" className="action-btn view-btn" onClick={onToggleExpand} title="View details">
              {isExpanded ? "Hide" : "View"}
            </button>
            <button type="button" className="action-btn pdf-btn" onClick={onDownloadPdf} title="Download PDF">
              PDF
            </button>
            <button type="button" className="action-btn delete-btn" onClick={onDelete} title="Delete">
              ✕
            </button>
          </div>
        </td>
      </tr>
      {isExpanded && (
        <tr className="detail-row">
          <td colSpan={5}>
            <div className="detail-grid">
              <DetailItem label="Hygiene" value={item.hygieneScore} isScore={true} />
              <DetailItem label="README" value={item.readmeScore} isScore={true} />
              <DetailItem label="Structure" value={item.structureScore} isScore={true} />
              <DetailItem label="Commits" value={item.commitQualityScore} isScore={true} />
              <DetailItem label="Documentation" value={item.documentationScore} isScore={true} />
              <DetailItem label="Code Quality" value={item.codeQualityScore} isScore={true} />
              <DetailItem label="Confidence" value={item.overallConfidence
                ? `${Math.round(item.overallConfidence * 100)}%` : "N/A"} />
              <DetailItem label="Recommendations" value={item.recommendationCount ?? 0} />
            </div>
          </td>
        </tr>
      )}
    </>
  );
}

function DetailItem({ label, value, isScore = false }) {
  return (
    <div className="detail-item">
      <span>{label}</span>
      <strong>{value ?? "N/A"}{(isScore && typeof value === "number") ? "/100" : ""}</strong>
    </div>
  );
}

export default HistoryPage;
