import { useState } from "react";
import { analyzeRepository, getPdfReportUrl, getErrorMessage } from "../api.js";

const LOADING_STEPS = [
  "Connecting to GitHub...",
  "Fetching repository data...",
  "Analyzing code quality...",
  "Generating scores...",
  "Building recommendations...",
];

function AnalyzePage() {
  const [repositoryUrl, setRepositoryUrl] = useState("");
  const [loading, setLoading] = useState(false);
  const [loadingStep, setLoadingStep] = useState(0);
  const [analysis, setAnalysis] = useState(null);
  const [error, setError] = useState("");
  const [recommendationFilter, setRecommendationFilter] = useState("ALL");

  const handleAnalyze = async () => {
    const trimmed = repositoryUrl.trim();
    if (!trimmed) {
      setError("Please enter a GitHub repository URL.");
      return;
    }

    const match = trimmed.match(/^https?:\/\/github\.com\/([^/]+)\/([^/]+)\/?$/i);
    if (!match) {
      setError("Enter a valid public GitHub repository URL (e.g., https://github.com/owner/repo).");
      return;
    }

    setLoading(true);
    setError("");
    setAnalysis(null);
    setRecommendationFilter("ALL");
    setLoadingStep(0);

    const stepInterval = setInterval(() => {
      setLoadingStep((prev) => (prev < LOADING_STEPS.length - 1 ? prev + 1 : prev));
    }, 3000);

    try {
      const data = await analyzeRepository(trimmed);
      setAnalysis(data);
    } catch (err) {
      setError(getErrorMessage(err));
    } finally {
      clearInterval(stepInterval);
      setLoading(false);
    }
  };

  const downloadPdf = () => {
    if (!analysis?.repository) return;
    const url = getPdfReportUrl(analysis.repository.owner, analysis.repository.repository);
    window.open(url, "_blank");
  };

  const formatLabel = (value) => {
    if (!value) return "Unknown";
    return String(value)
      .replaceAll("_", " ")
      .replace(/\b\w/g, (c) => c.toUpperCase());
  };

  const recommendations = Array.isArray(analysis?.recommendations)
    ? analysis.recommendations
    : [];

  const filteredRecommendations = recommendations.filter(
    (r) => recommendationFilter === "ALL" || r.priority === recommendationFilter
  );

  const scoreItems = [
    { label: "Repository Hygiene", result: analysis?.hygiene },
    { label: "README Quality", result: analysis?.readme },
    { label: "Project Structure", result: analysis?.structure },
    { label: "Commit Quality", result: analysis?.commitQuality },
    { label: "Documentation", result: analysis?.documentation },
    { label: "Code Quality", result: analysis?.codeQuality },
  ];

  return (
    <>
      {/* Hero Section */}
      <section className="hero">
        <div className="hero-badge">GitHub Repository Quality Analysis</div>
        <h2>
          Diagnose your repository.
          <br />
          <span>Improve your codebase.</span>
        </h2>
        <p>
          Analyze repository hygiene, documentation, project structure,
          commit quality and code quality with actionable recommendations.
        </p>
      </section>

      {/* Analyzer Input */}
      <section className="analyzer-card">
        <div className="card-heading">
          <h3>Analyze Repository</h3>
          <p>Enter a public GitHub repository URL.</p>
        </div>

        <div className="input-group">
          <input
            type="text"
            value={repositoryUrl}
            onChange={(e) => setRepositoryUrl(e.target.value)}
            onKeyDown={(e) => { if (e.key === "Enter" && !loading) handleAnalyze(); }}
            placeholder="https://github.com/owner/repository"
            disabled={loading}
          />
          <button type="button" onClick={handleAnalyze} disabled={loading}>
            {loading ? (
              <>
                <span className="spinner" /> {LOADING_STEPS[loadingStep]}
              </>
            ) : (
              "Analyze Repository"
            )}
          </button>
        </div>

        {error && (
          <div className="error-message" role="alert">
            <strong>Error:</strong> {error}
          </div>
        )}
      </section>

      {/* Loading Progress */}
      {loading && (
        <section className="loading-section">
          <div className="loading-progress">
            {LOADING_STEPS.map((step, index) => (
              <div
                key={step}
                className={`loading-step ${index < loadingStep ? "done" : ""} ${index === loadingStep ? "active" : ""}`}
              >
                <div className="step-indicator">
                  {index < loadingStep ? "✓" : index === loadingStep ? <span className="dot-pulse" /> : (index + 1)}
                </div>
                <span>{step}</span>
              </div>
            ))}
          </div>
        </section>
      )}

      {/* Analysis Results */}
      {analysis && (
        <section className="results-section">
          {/* Header */}
          <div className="results-header">
            <div>
              <span className="section-label">REPOSITORY ANALYSIS</span>
              <h3 className="repository-name">
                {analysis.repository?.owner}/{analysis.repository?.repository}
              </h3>
              <div className="repository-meta">
                <span className="health-badge">{formatLabel(analysis.overall?.repositoryHealth)}</span>
                {analysis.repository?.archived && <span className="archived-badge">Archived</span>}
              </div>
            </div>
            <div className="results-actions">
              <button type="button" className="secondary-button" onClick={downloadPdf}>
                Download PDF Report
              </button>
            </div>
          </div>

          {/* Repository Info */}
          <div className="section-header">
            <span className="section-label">REPOSITORY INFORMATION</span>
            <h3>Repository Details</h3>
          </div>

          <div className="repo-info-grid">
            <InfoItem label="Owner" value={analysis.repository?.owner} />
            <InfoItem label="Name" value={analysis.repository?.repository} />
            <InfoItem label="Description" value={analysis.repository?.description || "No description"} />
            <InfoItem label="Stars" value={analysis.repository?.stars} />
            <InfoItem label="Forks" value={analysis.repository?.forks} />
            <InfoItem label="Watchers" value={analysis.repository?.watchers} />
            <InfoItem label="Open Issues" value={analysis.repository?.openIssues} />
            <InfoItem label="Language" value={analysis.repository?.language || "Not specified"} />
            <InfoItem label="License" value={analysis.repository?.license || "None"} />
            <InfoItem label="Default Branch" value={analysis.repository?.defaultBranch} />
            <InfoItem label="Last Updated" value={analysis.repository?.lastUpdated
              ? new Date(analysis.repository.lastUpdated).toLocaleDateString() : "—"} />
          </div>

          {/* Overall Score */}
          <div className="overview-grid">
            <MetricCard label="Final Score" value={analysis.overall?.score} suffix="/100" featured />
            <MetricCard label="Base Score" value={analysis.overall?.baseScore} suffix="/100" />
            <MetricCard label="Grade" value={analysis.overall?.grade} />
            <MetricCard label="Repository Type" value={formatLabel(analysis.overall?.repositoryType)} />
            <MetricCard label="Recommendations" value={recommendations.length} />
            <MetricCard label="Health" value={formatLabel(analysis.overall?.repositoryHealth)} />
          </div>

          {/* Adjustments */}
          {analysis.overall?.adjustments && Object.keys(analysis.overall.adjustments).length > 0 && (
            <div className="partial-warning" style={{marginTop: "20px"}}>
              <h4>⚖️ Scoring Adjustments</h4>
              <ul className="warning-list">
                {Object.entries(analysis.overall.adjustments).map(([key, val], i) => (
                  <li key={i}><strong>{key}:</strong> {val}</li>
                ))}
              </ul>
            </div>
          )}

          {/* Warnings */}
          {analysis.warnings?.length > 0 && (
            <div className="partial-warning" role="alert" style={{marginTop: "20px"}}>
              <h4>⚠️ Analysis Notes</h4>
              <ul className="warning-list">
                {analysis.warnings.map((w, i) => <li key={i}>{w}</li>)}
              </ul>
            </div>
          )}

          {/* Score Breakdown */}
          <div className="section-header">
            <span className="section-label">QUALITY ANALYSIS</span>
            <h3>Score Breakdown</h3>
            <p>Repository quality across all analysis dimensions.</p>
          </div>

          <div className="score-breakdown-grid">
            {scoreItems.map((item) => (
              <ScoreCard key={item.label} label={item.label} result={item.result} formatLabel={formatLabel} />
            ))}
          </div>

          {/* Recommendations */}
          {recommendations.length > 0 && (
            <>
              <div className="section-header">
                <span className="section-label">RECOMMENDATIONS</span>
                <h3>Actionable Recommendations</h3>
                <p>Prioritized improvements for your repository.</p>
              </div>

              <div className="filter-tabs">
                {["ALL", "HIGH", "MEDIUM", "LOW"].map((filter) => (
                  <button
                    type="button"
                    key={filter}
                    className={`filter-tab ${recommendationFilter === filter ? "active" : ""}`}
                    onClick={() => setRecommendationFilter(filter)}
                  >
                    {filter}
                  </button>
                ))}
              </div>

              <div className="recommendation-list">
                {filteredRecommendations.map((rec, index) => (
                  <article className="recommendation-card" key={index}>
                    <div className="recommendation-meta">
                      <span className={`priority-badge priority-${String(rec.priority).toLowerCase()}`}>
                        {formatLabel(rec.priority)}
                      </span>
                      <span className="category-badge">{formatLabel(rec.category)}</span>
                    </div>
                    <h4>{rec.title}</h4>
                    <p>{rec.description}</p>
                    {Array.isArray(rec.affectedItems) && rec.affectedItems.length > 0 && (
                      <div className="affected-items">
                        <span>Affected Items</span>
                        <ul>
                          {rec.affectedItems.map((item, i) => <li key={i}>{String(item)}</li>)}
                        </ul>
                      </div>
                    )}
                  </article>
                ))}
              </div>
            </>
          )}
        </section>
      )}
    </>
  );
}

function MetricCard({ label, value, suffix = "", featured = false }) {
  return (
    <div className={`metric-card ${featured ? "featured" : ""}`}>
      <span>{label}</span>
      <strong>
        {value ?? "N/A"}
        {suffix && <small>{suffix}</small>}
      </strong>
    </div>
  );
}

function InfoItem({ label, value }) {
  return (
    <div className="info-item">
      <span className="info-label">{label}</span>
      <span className="info-value">{value ?? "—"}</span>
    </div>
  );
}

function ScoreCard({ label, result, formatLabel }) {
  const score = result?.score;
  const isMissing = score === null || score === undefined;
  const safeScore = isMissing ? 0 : Number(score);
  const confidence = result?.confidence;
  const status = result?.status || "UNKNOWN";

  return (
    <div className="score-breakdown-card">
      <div className="score-card-header">
        <span>{label}</span>
        <strong>{isMissing ? "N/A" : `${safeScore}/100`}</strong>
      </div>
      <div className="score-card-meta">
        <span className={`status-chip status-${String(status).toLowerCase()}`}>
          {formatLabel(status)}
        </span>
        <span>
          Confidence: {typeof confidence === "number" ? `${Math.round(confidence * 100)}%` : "N/A"}
        </span>
      </div>
      <div className="score-track">
        <div className="score-fill" style={{ width: `${Math.min(safeScore, 100)}%` }} />
      </div>
    </div>
  );
}

export default AnalyzePage;
