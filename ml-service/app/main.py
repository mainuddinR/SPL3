from fastapi import FastAPI
from app.api.routes import prediction, health
from app.core.config import settings

app = FastAPI(title=settings.app_name)

app.include_router(health.router, prefix="/health", tags=["health"])
app.include_router(prediction.router, prefix="/api/v1", tags=["prediction"])

if __name__ == "__main__":
    import uvicorn
    uvicorn.run("app.main:app", host="0.0.0.0", port=settings.port, reload=True)
