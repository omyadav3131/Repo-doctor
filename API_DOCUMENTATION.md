# 🔌 API Documentation

RepoDoctor provides a suite of RESTful APIs to trigger, monitor, and retrieve repository quality analysis reports.

---

## 1. Initiate Repository Analysis

Triggers a synchronous analysis of a specified GitHub repository. Since analysis can take several seconds to complete, this endpoint will block for up to 90 seconds until the results are ready.

- **Method:** `POST`
- **URL:** `/api/analyze`
- **Content-Type:** `application/json`

### Request Body
```json
{
  "repositoryUrl": "https://github.com/expressjs/express"
}
```

### Response (200 OK)
Returns the completed analysis snapshot.

```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "status": "COMPLETED",
  "overall": {
    "score": 85,
    "grade": "A",
    "repositoryHealth": "GOOD",
    "repositoryType": "NODE"
  },
  "hygiene": {
    "score": 90,
    "evidence": ["No dirty files found", "Clear standard structure"]
  },
  "recommendations": [
    {
      "title": "Add a contributing guide",
      "priority": "HIGH"
    }
  ]
}
```

### Error Responses
- `400 Bad Request`: Invalid GitHub URL format.
- `404 Not Found`: Repository does not exist or requires authentication.
- `403 Forbidden`: GitHub API Rate Limit exceeded.
- `504 Gateway Timeout`: The analysis took longer than 90 seconds to complete.

---

## 2. Retrieve Historical Analyses

Fetches a paginated list of all previously conducted analyses across all repositories, ordered by the most recent.

- **Method:** `GET`
- **URL:** `/api/analyses`

### Response (200 OK)
```json
[
  {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "repositoryUrl": "https://github.com/expressjs/express",
    "overallScore": 85,
    "grade": "A",
    "analysisDate": "2026-07-18T10:00:00Z"
  }
]
```

---

## 3. Delete Analysis History

Deletes a specific historical analysis record by its ID.

- **Method:** `DELETE`
- **URL:** `/api/analyses/{id}`

### Response (200 OK)
```json
{
  "deletedAnalysisId": 123,
  "status": "ANALYSIS_DELETED"
}
```

---

## 4. Download PDF Report

Generates and downloads a professional, formatted PDF report of the most recent analysis for a given repository.

- **Method:** `GET`
- **URL:** `/api/analyses/{owner}/{repositoryName}/report/pdf`
- **Produces:** `application/pdf`

### Response (200 OK)
Returns a binary PDF file stream containing the executive summary, dimension breakdown, bar charts, and actionable recommendations.

### Headers
```http
Content-Type: application/pdf
Content-Disposition: attachment; filename="RepoDoctor-express-Report.pdf"
```

---

## 5. System Health Check

Returns the operational status of the RepoDoctor backend engine.

- **Method:** `GET`
- **URL:** `/api/health`

### Response (200 OK)
```json
{
  "status": "UP",
  "timestamp": "2026-07-18T10:05:00Z",
  "githubApiConnected": true
}
```
