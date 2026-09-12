from pydantic_settings import BaseSettings, SettingsConfigDict
from typing import Literal

class Settings(BaseSettings):
    app_name: str = "SATD ML Service"
    port: int = 8000
    log_level: str = "info"
    classifier_type: Literal["mock", "codebert"] = "mock"
    model_name: str = "microsoft/codebert-base"
    model_path: str | None = None

    model_config = SettingsConfigDict(env_file=".env", env_file_encoding="utf-8")

settings = Settings()
