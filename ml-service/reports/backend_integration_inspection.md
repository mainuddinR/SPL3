# Backend SATD Integration Inspection

## 1. Current Spring Boot Architecture Relevant to SATD Analysis
- **Controller:** `PullRequestController.java` serves standard GitHub entity data (PRs, files, comments) to the frontend via basic CRUD endpoints.
- **Service:** `SatdExtractionService.java` is an isolated service capable of parsing unified diffs (`patch`), escaping string/char literals, and extracting comments with surrounding context lines.
- **DTOs:** `SatdCandidateDTO` exists to hold extracted comments but is currently unused.
- **Configuration:** `application.yml` already contains the property `app.ml-service.url: http://localhost:8000`.
- **HTTP Client:** Built-in `RestTemplate` and `RestClient` are available via `spring-boot-starter-web`.

## 2. Exact Data Flow Currently Implemented
**Current Flow:** 
GitHub API → `GithubService` → saves to H2/Oracle DB. 
Frontend → `PullRequestController` → fetches raw `PullRequestFile` entities from DB.
**Missing Link:** `SatdExtractionService` and the FastAPI ML model are **completely completely disconnected** from this flow.

## 3. SatdExtractionService Input/Output
- **Input:** `PullRequestFile` (specifically targeting the `.java` files and their `patch` diff strings).
- **Output:** `List<SatdCandidateDTO>`.
- **Comment Extraction:** Uses a state-machine character parser looping over patch lines to extract `//` and `/* */` comments while correctly ignoring strings (`"..."`) and chars (`'...'`).
- **Code Extraction:** It does **NOT** separate preceding and succeeding code. It extracts a `contextStart` index (up to 5 lines before the comment) and `contextEnd` index (up to 5 lines after) and concatenates them all (including the comment itself) into a single `surroundingCode` string.
- **File/Line Preservation:** Yes. It parses the unified diff hunk header (`@@ -x,y +a,b @@`) to track `currentNewLineNumber` and passes it along with `file.getFilename()` into the DTO.

## 4. PullRequestController Current Behavior
`PullRequestController` simply exposes raw JPA entity endpoints (e.g., `GET /api/prs/{prId}/files` returning `List<PullRequestFile>`). It performs no analysis and calls no external ML services.

## 5. Existing DTO Structures
`SatdCandidateDTO` currently has:
```java
private String commentText;
private String surroundingCode; // <--- GAP: ML API expects preceding and succeeding split
private String filename;
private String language;
private Integer lineNumber;
```
There is no DTO for the FastAPI Request or FastAPI Response.

## 6. Existing HTTP Client / Dependency Options
`pom.xml` includes `spring-boot-starter-web`. 
- **Option 1:** `RestClient` (Modern, fluent, synchronous HTTP client available in Spring Boot 3.2+). **(Recommended)**
- **Option 2:** `RestTemplate` (Legacy synchronous client).
*Note: `WebClient` and `Feign` are not in the classpath and shouldn't be added to keep minimal changes.*

## 7. Exact Gap Between Current Backend and FastAPI CodeBERT
1. **Contract Mismatch:** `SatdExtractionService` provides a merged `surroundingCode` block, but FastAPI requires separated `preceding_code` and `succeeding_code`.
2. **Missing Integration:** No service actively routes the `PullRequestFile`s through the `SatdExtractionService`.
3. **Missing ML Client:** No Java code actually executes the HTTP POST to `http://localhost:8000/api/v1/satd-detect`.
4. **Missing UI Delivery:** No controller endpoint provides the combined `File + SATD Findings` payload to the Angular frontend.

## 8. Recommended MINIMAL Files to Modify
1. **`SatdCandidateDTO.java`**: Split `surroundingCode` into `precedingCode` and `succeedingCode`.
2. **`SatdExtractionService.java`**: Update `createCandidate()` to populate the split fields.
3. **`CodeBertClientService.java` (NEW)**: Create a simple `@Service` using `RestClient` to call FastAPI.
4. **`PullRequestController.java`**: Add a new endpoint `POST /api/prs/{prId}/analyze` that orchestrates the extraction, CodeBERT classification, and saving results to `SATDFindingRepository`.

## 9. Recommended Implementation Order
1. Update `SatdCandidateDTO` and `SatdExtractionService` to split the context.
2. Create `CodeBertRequest` and `CodeBertResponse` DTOs in Java to match FastAPI exactly.
3. Build the `CodeBertClientService` using Spring's `RestClient` and the existing `app.ml-service.url` property.
4. Add the `analyze` endpoint to `PullRequestController` to tie it all together.

## 10. Proposed Request/Response Contract (Spring Boot ↔ FastAPI)
**Spring Boot Request to FastAPI:**
```json
{
  "comment": "// TODO: fix this temporary workaround later",
  "preceding_code": "public void process() {",
  "succeeding_code": "    return;\n}"
}
```
**FastAPI Response to Spring Boot:**
```json
{
  "label": "SATD",
  "satd_probability": 0.98,
  "non_satd_probability": 0.02,
  "confidence": 0.98
}
```

## 11. Proposed Combined Response Contract (Spring Boot ↔ Angular)
Instead of heavily altering the raw `PullRequestFile` endpoint, the `analyze` endpoint should return the saved `SATDFinding` entities:
```json
[
  {
    "id": 1,
    "filename": "src/main/UserService.java",
    "lineNumber": 45,
    "commentText": "// FIXME: Handle null pointer here",
    "debtType": "SATD",
    "severityScore": null, // For later implementation
    "aiSuggestion": null   // For later implementation
  }
]
```
The frontend can then overlay these findings onto the file diff viewer.

## 12. Blockers Before Implementation
- **Splitting Context:** `SatdExtractionService` must be updated to slice the `hunkLines` array differently to provide `preceding_code` and `succeeding_code` without the comment line in the middle.
- Otherwise, there are no structural blockers. The `application.yml` is ready, the REST client is available, and the FastAPI endpoint is already verified.
