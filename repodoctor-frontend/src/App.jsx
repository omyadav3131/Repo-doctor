import { Routes, Route, Link, useLocation } from "react-router-dom";
import AnalyzePage from "./pages/AnalyzePage.jsx";
import HistoryPage from "./pages/HistoryPage.jsx";
import "./App.css";

function App() {
  const location = useLocation();

  return (
    <div className="app">
      <header className="navbar">
        <div className="navbar-inner">
          <Link to="/" className="brand">
            <div className="brand-icon">R</div>
            <div>
              <h1>RepoDoctor</h1>
              <span>Repository Health Analyzer</span>
            </div>
          </Link>

          <nav className="nav-links">
            <Link
              to="/"
              className={`nav-link ${location.pathname === "/" ? "active" : ""}`}
            >
              Analyze
            </Link>
            <Link
              to="/history"
              className={`nav-link ${location.pathname === "/history" ? "active" : ""}`}
            >
              History
            </Link>
          </nav>
        </div>
      </header>

      <main className="main-content">
        <Routes>
          <Route path="/" element={<AnalyzePage />} />
          <Route path="/history" element={<HistoryPage />} />
        </Routes>
      </main>
    </div>
  );
}

export default App;