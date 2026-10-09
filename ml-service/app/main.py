from contextlib import asynccontextmanager
from fastapi import FastAPI
from app.api.routes import prediction, health
from app.core.config import settings


@asynccontextmanager
async def lifespan(_app: FastAPI):
    # Fail startup if the configured real model cannot load; HTTP health is then meaningful.
    # Cached dependency ensures all classification requests reuse this preloaded instance.
    if settings.classifier_type == "codebert":
        prediction.get_classifier()
    yield


app = FastAPI(title=settings.app_name, lifespan=lifespan)

app.include_router(health.router, prefix="/health", tags=["health"])
app.include_router(prediction.router, prefix="/api/v1", tags=["prediction"])

if __name__ == "__main__":
    import uvicorn
    uvicorn.run("app.main:app", host="0.0.0.0", port=settings.port, reload=True)
