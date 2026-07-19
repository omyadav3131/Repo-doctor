# RepoDoctor Scoring Methodology

## 1. Metrics & Weights
RepoDoctor computes an overall score from up to six distinct analysis dimensions, each with a specific weight:

- **Code Quality**: 25%
- **Project Structure**: 15%
- **Hygiene**: 15%
- **README**: 15%
- **Commit Quality**: 15%
- **Documentation**: 15%

## 2. Supported Evidence & Languages
- **Source Code**: Supports `.java`, `.py`, `.js`, `.ts`, `.jsx`, `.tsx`, `.php`, `.cs`, `.cpp`, `.c`, `.go`, `.rb`. (Other files are ignored).
- **Structure**: Looks for `src`, `test`, `tests`, `docs`, `config`, `.github`, `pom.xml`, `package.json`, `build.gradle`, `docker-compose.yml`, `Dockerfile`, `.env.example`.
- **Commits**: Analyzes up to 100 recent commits for meaningful messages, issue references (`Fixes #123`), and conventional commit formats (`feat:`, `fix:`).

## 3. No-Data and Partial Analysis Behavior
A dimension might not have enough evidence to be analyzed. We define an explicit `AnalysisStatus`:
- **SUCCESS**: Data was successfully analyzed and a score (0-100) was produced.
- **NOT_ANALYZABLE**: The repository lacks the required data to score this dimension (e.g., zero source files, no README). The dimension is excluded from the overall score calculation. The score is explicitly `null`.
- **PARTIAL**: Some data is available, but the score may not be fully representative.

## 4. Coverage Formula & Overall Score Aggregation
The overall score is a weighted average of only the **SUCCESS** or **PARTIAL** dimensions. Dimensions that are **NOT_ANALYZABLE** are removed from the denominator to avoid unfairly penalizing (Score 0) or falsely rewarding (Score 100) the repository.

Formula:
`Overall Score = SUM(Score_i * Weight_i) / SUM(Weight_i for valid dimensions)`

If no dimensions are valid, the repository cannot be scored and receives a null overall score.

## 5. Confidence Rules
Overall confidence is calculated based on how many dimensions were successfully analyzed:
`Confidence = Valid Dimension Count / Total Possible Dimensions (6)`
- If `Confidence < 0.5`, the classification degrades significantly, indicating a sparse repository.

## 6. Sparse Repository Gate
Sparse repositories (e.g., ones with no code, no meaningful commits, or no structure) cannot receive a confident `GOOD` or `VERY_GOOD` health classification, regardless of their partial score in available dimensions. Sparse repositories are typically classified as `NEEDS_REVIEW` or `CRITICAL_RISK`.

## 7. Heuristic Limitations & Sampling
- Commit analysis does not parse diff sizes, it only evaluates commit messages.
- Code Quality looks for regex patterns (TODOs, FIXMEs, possible secrets) rather than building an Abstract Syntax Tree (AST).
- The README and documentation analyzers use word counts and keyword checks rather than NLP-based semantic understanding.
- **Deterministic Sampling:** To prevent unpredictable scores and GitHub API timeouts, large repositories have their valid files sorted alphabetically, and only the first 50 are sampled for deep regex inspection.
- **Duplicate Detection:** Duplicate file detection relies on strict raw content comparison (hashing/string matching) of files with identical names, completely avoiding filename-only blacklists and false positives for standard framework files (e.g., `index.js`, `__init__.py`).
