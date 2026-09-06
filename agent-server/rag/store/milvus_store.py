"""Thin dense-search wrapper around the existing Milvus client."""

from __future__ import annotations

from typing import Any

from rag.milvus_client import MedicalRagMilvus
from rag.rag_config import RAG_SPARSE_FIELD
from rag.rag_schema import MILVUS_OUTPUT_FIELDS


class MilvusStore:
    def __init__(self, client: MedicalRagMilvus):
        self.client = client

    @property
    def collection_name(self) -> str:
        return self.client.collection_name

    def has_collection(self) -> bool:
        return self.client.has_collection()

    def search_dense(
        self,
        vector: list[float],
        top_k: int,
        doc_type: str | None = None,
        output_fields: list[str] | None = None,
    ) -> list[dict[str, Any]]:
        return self.client.search(vector, top_k=top_k, doc_type=doc_type, output_fields=output_fields)

    def search_sparse(
        self,
        query: str,
        top_k: int,
        doc_type: str | None = None,
        output_fields: list[str] | None = None,
    ) -> list[dict[str, Any]]:
        self.client._ensure_connected()
        if not self.client.has_collection():
            raise RuntimeError(f"collection 不存在: {self.client.collection_name}")
        self.client.client.load_collection(self.client.collection_name)
        filter_expr = f'doc_type == "{doc_type}"' if doc_type else ""
        return self.client.client.search(
            collection_name=self.client.collection_name,
            data=[query],
            anns_field=RAG_SPARSE_FIELD,
            limit=top_k,
            filter=filter_expr,
            output_fields=output_fields or MILVUS_OUTPUT_FIELDS,
            search_params={"metric_type": "BM25", "params": {}},
        )[0]
