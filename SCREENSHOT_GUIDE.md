# 📸 RepoDoctor Screenshot Guide

To ensure your GitHub portfolio looks as professional as possible, capture the following screenshots from the running application and place them in an `assets/` or `images/` folder within the root directory.

---

## 1. Landing / Analyze Page
- **State to capture:** The initial load of the application (`http://localhost:5173/`).
- **Focus:** The hero section and the URL input field.
- **Suggested Caption:** 
  > *The clean, dark-mode ready landing page allowing users to instantly audit any public or private GitHub repository.*
- **File Name:** `landing-page.png`

## 2. In-Progress Loader
- **State to capture:** Right after submitting a repository URL for analysis.
- **Focus:** The skeleton loader / spinner indicating background processing.
- **Suggested Caption:** 
  > *RepoDoctor fetches and parses thousands of lines of code in seconds via concurrent API streaming.*
- **File Name:** `analysis-loading.png`

## 3. Executive Summary / Overall Results
- **State to capture:** The top half of the results page after a successful analysis (e.g., `expressjs/express`).
- **Focus:** The Overall Score (e.g. 68/100), the Grade (e.g. C), the Repository Health badge, and the primary analysis metadata.
- **Suggested Caption:** 
  > *The Executive Summary provides an immediate, high-level overview of the project's health and structural integrity.*
- **File Name:** `executive-summary.png`

## 4. Dimension Breakdown Panel
- **State to capture:** Scroll down to the 6-dimension scoring grid on the results page.
- **Focus:** The individual cards for Code Quality, Hygiene, Documentation, Project Structure, Commits, and README.
- **Suggested Caption:** 
  > *The 6-pillar analysis engine breaks down exact strengths and weaknesses, dynamically redistributing weights based on repository classifications.*
- **File Name:** `dimension-breakdown.png`

## 5. Actionable Recommendations
- **State to capture:** The bottom section of the results page detailing specific fixes.
- **Focus:** The prioritized list of recommendations (High/Medium/Low priority tags).
- **Suggested Caption:** 
  > *Context-aware recommendations instruct developers exactly how to boost their project's score and code quality.*
- **File Name:** `recommendations.png`

## 6. History Dashboard
- **State to capture:** The `/history` route.
- **Focus:** The tabular layout of previously analyzed repositories, search functionality, and delete buttons.
- **Suggested Caption:** 
  > *The historical dashboard allows users to retrieve past JSON snapshots instantly, powered by a fast PostgreSQL database.*
- **File Name:** `history-dashboard.png`

## 7. PDF Report Export
- **State to capture:** A screenshot of the downloaded PDF opened in a viewer.
- **Focus:** The generated JFreeChart bar graph and the PDF layout.
- **Suggested Caption:** 
  > *Generate professional, boardroom-ready PDF audit reports instantly using OpenPDF and JFreeChart.*
- **File Name:** `pdf-report.png`

---

**Tip:** For the best quality, use a tool like "GoFullPage" or your browser's built-in DevTools to take full-page captures, and ensure your browser window is maximized.
