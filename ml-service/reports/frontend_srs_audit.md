# Frontend SRS Compliance Audit

## 1. CURRENT FRONTEND ARCHITECTURE
The current frontend is an Angular 17+ single-page application (using standalone components). 

**Key Components:**
- **Framework:** Angular 17+ with TypeScript
- **Styling:** CSS/SCSS with Tailwind (configured in `tailwind.config.js`)
- **Routing Structure:**
  - `/login` → GitHub Authentication
  - `/dashboard` → Main layout wrapper
  - `/dashboard/repos` → Repository List
  - `/dashboard/projects/:projectId/pulls` → Pull Request List
  - `/dashboard/pulls/:prId` → Pull Request Details (Overview, Files, Comments)
- **Services:** `github.service.ts`, `pull-request.service.ts`
- **API Integration:** Connects exclusively to the Java Spring Boot backend (`http://localhost:8080/api/prs`).
- **State:** It acts as a generic GitHub PR viewer. There is **no state, model, or UI code** related to Technical Debt, CodeBERT, SATD, Severity, or AI Suggestions.

---

## 2. SRS REQUIREMENTS SUMMARY
Based on the extracted `SPL-3_Project_Proposal.docx`, the frontend must support:
- **Core AI Integration:** Display SATD classifications (Design, Defect, Test, Requirement, Documentation Debt).
- **Context-Aware Review:** Display SATD detected from code comments and surrounding code context.
- **Severity Scoring:** Show severity scores (1-5) and highlight risky code locations.
- **AI-Based Fix Suggestions:** Display LLM-generated refactoring suggestions inside PR reviews.
- **Web Dashboard:** Interactive dashboard showing:
  - Repository overview & SATD count summary
  - PR analysis history
  - Severity distribution charts
  - SATD statistics per repository
- **Authentication:** GitHub login integration (Implemented).

---

## 3. SRS COMPLIANCE TABLE

| ID | SRS Requirement | Current Implementation | File/Path | Status | Gap |
|---|---|---|---|---|---|
| 1 | GitHub PR Integration | Fetches Repos, PRs, Files, and Comments | `app/features/pull-requests/` | **IMPLEMENTED** | None |
| 2 | GitHub Login Auth | Working login flow | `app/features/login/` | **IMPLEMENTED** | None |
| 3 | Detect SATD & Context | Does not send context or receive SATD | N/A | **MISSING** | Missing API payload & UI logic |
| 4 | Display Classification | No UI for CodeBERT classes | N/A | **MISSING** | Missing badge/tag UI for Debt Types |
| 5 | Severity Scoring (1-5) | No severity metrics exist in UI | N/A | **MISSING** | Missing severity charts/indicators |
| 6 | AI Fix Suggestions | No suggestion UI | N/A | **MISSING** | Missing inline LLM suggestion cards |
| 7 | Highlight Risky Code | Shows raw diffs only | `pull-request-details` | **MISSING** | Missing inline SATD highlights |
| 8 | SATD Count Summary | Dashboard is empty / basic | `app/features/dashboard/` | **MISSING** | Missing statistical components |
| 9 | Severity Charts | No charts implemented | `app/features/dashboard/` | **MISSING** | Missing chart.js/d3 integration |

---

## 4. CRITICAL MISSING REQUIREMENTS
- **No SATD Awareness:** The frontend has absolutely zero awareness of Technical Debt. Searching the codebase for `satd`, `codebert`, or `debt` yields zero results.
- **No Surrounding Code Context Assembly:** The SRS mandates sending comments *plus* surrounding code context. The frontend currently only fetches raw PR patches and comments from the Java backend, but does not prepare or display contextual AI payloads.
- **No Dashboard Analytics:** The "Interactive Dashboard" with heatmaps and severity distribution charts is completely unbuilt.

---

## 5. PARTIAL / INCORRECT IMPLEMENTATIONS
- **PR Details View:** The `pull-request-details.html/ts` file successfully fetches a PR's files and comments. However, it displays them as a standard generic viewer (like GitHub's default UI) instead of a **Code Review Analyzer**. It needs to be augmented to highlight specific lines that the ML backend flagged as SATD.

---

## 6. UNNECESSARY GITHUB-DERIVED FEATURES
- **Harmless Extra:** The frontend feels like a direct clone of a basic GitHub PR viewer tutorial. While not inherently bad, it focuses entirely on standard CRUD operations for PRs rather than the core thesis of the project (AI Analysis). There are no conflicting or malicious features, it is simply heavily under-developed regarding the ML aspects.

---

## 7. FRONTEND ↔ FASTAPI ↔ CODEBERT INTEGRATION STATUS
**Status: 0% Integrated**

- The frontend currently makes calls to `http://localhost:8080/api/prs` (Spring Boot).
- The Spring Boot backend **does not** call `http://localhost:8000/api/v1/satd-detect` (FastAPI). 
- While the Java backend *does* have a `SatdExtractionService.java` that properly extracts comments and +/- 5 lines of surrounding code into a `SatdCandidateDTO`, this service is completely disconnected from the `PullRequestController.java`.
- As a result, the frontend never receives any `label`, `satd_probability`, or `confidence` data to display.

**Contract Mismatch:**
- **Frontend expects:** Standard GitHub PR objects.
- **Verified API requires:** `{"comment": "...", "preceding_code": "...", "succeeding_code": "..."}`
- **Current Flow:** The connection is completely broken. The frontend never sends this payload, and the backend never provides the SATD response schema.

---

## 8. CURRENT USER FLOW
1. User logs in via GitHub.
2. User views a list of their Repositories.
3. User clicks a Repository to view Pull Requests.
4. User clicks a Pull Request to view its raw Overview, Files (diffs), and GitHub Comments.
*(End of flow. No AI analysis occurs).*

---

## 9. REQUIRED SRS USER FLOW
1. User logs in via GitHub.
2. User views Dashboard featuring **Severity Distribution Charts** and **SATD Statistics**.
3. User views Repositories with a **Repository Risk Level** badge.
4. User opens a Pull Request. An **"Analyze PR"** background task runs.
5. User views PR Files. Inline **Annotations** highlight risky code, displaying the **SATD Category** (e.g., Design Debt), **Severity Score** (1-5), and **AI Fix Suggestions**.

---

## 10. MINIMAL CHANGES REQUIRED TO MAKE FRONTEND SRS-COMPLIANT
1. **Java Backend:** Wire `SatdExtractionService` into `PullRequestController` to analyze PR files, send the `SatdCandidateDTO` payloads to the FastAPI `satd-detect` endpoint, and return the combined results to the frontend.
2. **Frontend Models:** Update `pull-request.model.ts` to include `satdLabel`, `satdProbability`, `severityScore`, and `fixSuggestion`.
3. **Frontend UI (PR Details):** Modify the file diff viewer to render red/yellow highlight boxes over lines flagged as SATD, displaying the CodeBERT classification badge.
4. **Frontend UI (Dashboard):** Integrate a charting library (like `chart.js`) to display the required severity distribution and SATD count summaries.

---

## 11. RECOMMENDED IMPLEMENTATION ORDER
1. **API Gateway (Java):** Connect Spring Boot to FastAPI so the Java API serves actual SATD data.
2. **Frontend Models & Services:** Update Angular interfaces to receive the new data.
3. **Inline Annotations (High Priority):** Update the PR Details component to highlight SATD directly on the code diffs (Core SRS Requirement).
4. **Dashboard Charts (Medium Priority):** Build the analytical charts required by the proposal.
5. **AI Fix Suggestions (Low Priority):** Implement the LLM suggestion UI.

---

## 12. FILES THAT WOULD NEED TO BE MODIFIED
**Backend (Java):**
- `PullRequestController.java`
- `SatdExtractionService.java` (needs FastAPI HTTP client logic)

**Frontend (Angular):**
- `src/app/models/pull-request.model.ts`
- `src/app/features/pull-requests/pull-request-details/pull-request-details.ts`
- `src/app/features/pull-requests/pull-request-details/pull-request-details.html`
- `src/app/features/dashboard/dashboard.ts`
- `src/app/features/dashboard/dashboard.html`

==================================================

# VERDICT

**NOT SRS COMPLIANT**

**Approximate Compliance Percentage: 20%**
*Justification: The frontend successfully satisfies the foundational requirements (GitHub authentication, Repository/PR fetching, Web Dashboard skeleton). However, it currently satisfies 0% of the core functional requirements (SATD detection, CodeBERT integration, Severity Scoring, Fix Suggestions, Contextual highlights, Analytical Charts) defined in the SRS.*
