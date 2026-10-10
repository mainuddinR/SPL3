# Angular Frontend

The Angular interface for the Automated Code Review & Technical Debt Analyzer. It provides GitHub login, repository and pull-request views, SATD findings, design-pattern results, notifications and settings.

## Development

Requires Node.js and npm.

```bash
npm ci
npm start
```

The development interface runs at **http://localhost:4200**. For the complete application, the Spring Boot backend and FastAPI ML service must also be available.

## Build and tests

```bash
npm run build
npm test
```

The production Docker image builds Angular and serves it through Nginx. See the root `README.md` for the full local setup.
