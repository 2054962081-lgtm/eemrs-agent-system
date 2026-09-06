"""Tokenizer helpers for Medical RAG chunking audits."""

from __future__ import annotations

from dataclasses import dataclass
from functools import lru_cache
from typing import Any

from rag.rag_config import EMBEDDING_MODEL_NAME


@dataclass(frozen=True)
class TokenizerInfo:
    model_name: str
    model_max_length: int
    tokenizer_class: str


class BgeTokenizer:
    def __init__(self, model_name: str = EMBEDDING_MODEL_NAME, local_files_only: bool = True):
        from transformers import AutoTokenizer

        self.model_name = model_name
        self.tokenizer: Any = AutoTokenizer.from_pretrained(model_name, local_files_only=local_files_only)

    @property
    def model_max_length(self) -> int:
        return int(getattr(self.tokenizer, "model_max_length", 512) or 512)

    @property
    def info(self) -> TokenizerInfo:
        return TokenizerInfo(
            model_name=self.model_name,
            model_max_length=self.model_max_length,
            tokenizer_class=self.tokenizer.__class__.__name__,
        )

    def count(self, text: str, add_special_tokens: bool = True) -> int:
        return len(self.tokenizer.encode(text or "", add_special_tokens=add_special_tokens, truncation=False))


@lru_cache(maxsize=2)
def get_bge_tokenizer(model_name: str = EMBEDDING_MODEL_NAME) -> BgeTokenizer:
    return BgeTokenizer(model_name=model_name)
