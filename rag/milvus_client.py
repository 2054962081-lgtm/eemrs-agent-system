"""Milvus client helpers for the medical RAG collection."""

from __future__ import annotations

from typing import Any

from .rag_config import MILVUS_COLLECTION_NAME, MILVUS_HOST, MILVUS_PORT, milvus_uri
from .rag_schema import MILVUS_OUTPUT_FIELDS


class MedicalRagMilvus:
    def __init__(
        self,
        host: str = MILVUS_HOST,
        port: str = MILVUS_PORT,
        collection_name: str = MILVUS_COLLECTION_NAME,
    ):
        self.host = host
        self.port = str(port)
        self.collection_name = collection_name
        try:
            from pymilvus import DataType, MilvusClient
        except ImportError as exc:
            raise RuntimeError("鏈畨瑁?pymilvus銆傝鍏堟墽琛?pip install -r requirements-rag.txt銆?) from exc
        self.DataType = DataType
        self.MilvusClient = MilvusClient
        self.client = None

    def connect(self) -> None:
        uri = milvus_uri(self.host, self.port)
        try:
            self.client = self.MilvusClient(uri=uri)
            self.client.list_collections()
        except Exception as exc:
            raise RuntimeError(
                f"鏃犳硶杩炴帴 Milvus锛岃纭 Docker 瀹瑰櫒宸插惎鍔細\n"
                f"  docker ps\n"
                f"骞剁‘璁?milvus-standalone 鏆撮湶绔彛 {self.port}銆傚綋鍓嶅湴鍧€: {self.host}:{self.port}"
            ) from exc
        print(f"Milvus connected: {self.host}:{self.port}")

    def has_collection(self) -> bool:
        self._ensure_connected()
        return self.client.has_collection(self.collection_name)

    def drop_collection(self) -> None:
        self._ensure_connected()
        if self.has_collection():
            self.client.drop_collection(self.collection_name)

    def create_collection(self, embedding_dim: int) -> None:
        self._ensure_connected()
        if self.has_collection():
            return
        schema = self.client.create_schema(auto_id=False, enable_dynamic_field=False)
        schema.add_field("chunk_id", self.DataType.VARCHAR, is_primary=True, max_length=128)
        schema.add_field("doc_id", self.DataType.VARCHAR, max_length=128)
        schema.add_field("doc_type", self.DataType.VARCHAR, max_length=64)
        schema.add_field("title", self.DataType.VARCHAR, max_length=512)
        schema.add_field("version", self.DataType.VARCHAR, max_length=32)
        schema.add_field("language", self.DataType.VARCHAR, max_length=32)
        schema.add_field("source_type", self.DataType.VARCHAR, max_length=128)
        schema.add_field("applicable_population", self.DataType.VARCHAR, max_length=1024)
        schema.add_field("related_symptoms", self.DataType.VARCHAR, max_length=1024)
        schema.add_field("related_departments", self.DataType.VARCHAR, max_length=1024)
        schema.add_field("urgency_level", self.DataType.VARCHAR, max_length=64)
        schema.add_field("content_json", self.DataType.VARCHAR, max_length=16000)
        schema.add_field("chunk_text", self.DataType.VARCHAR, max_length=16000)
        schema.add_field("embedding", self.DataType.FLOAT_VECTOR, dim=embedding_dim)

        index_params = self.client.prepare_index_params()
        try:
            index_params.add_index("embedding", index_type="AUTOINDEX", metric_type="COSINE")
        except Exception:
            index_params.add_index(
                "embedding",
                index_type="HNSW",
                metric_type="COSINE",
                params={"M": 16, "efConstruction": 200},
            )
        self.client.create_collection(
            collection_name=self.collection_name,
            schema=schema,
            index_params=index_params,
        )

    def ensure_collection(self, embedding_dim: int, reset: bool = False) -> None:
        self._ensure_connected()
        if reset and self.has_collection():
            self.drop_collection()
        if self.has_collection():
            current_dim = self.embedding_dim()
            if current_dim and current_dim != embedding_dim:
                raise RuntimeError(
                    f"collection 宸插瓨鍦ㄤ絾鍚戦噺缁村害涓嶄竴鑷? 褰撳墠 {current_dim}, 妯″瀷 {embedding_dim}銆?
                    " 璇蜂娇鐢?--reset 閲嶅缓銆?
                )
        else:
            self.create_collection(embedding_dim)
        print(f"Collection ready: {self.collection_name}, embedding_dim={embedding_dim}")

    def embedding_dim(self) -> int | None:
        self._ensure_connected()
        if not self.has_collection():
            return None
        description = self.client.describe_collection(self.collection_name)
        for field in description.get("fields", []):
            if field.get("name") == "embedding":
                params = field.get("params") or {}
                return int(params.get("dim") or field.get("dim") or 0)
        return None

    def existing_chunk_ids(self, chunk_ids: list[str]) -> set[str]:
        self._ensure_connected()
        if not chunk_ids or not self.has_collection():
            return set()
        quoted = ", ".join(json_quote(chunk_id) for chunk_id in chunk_ids)
        rows = self.client.query(
            collection_name=self.collection_name,
            filter=f"chunk_id in [{quoted}]",
            output_fields=["chunk_id"],
        )
        return {row["chunk_id"] for row in rows}

    def insert(self, rows: list[dict[str, Any]]) -> int:
        self._ensure_connected()
        result = self.client.insert(collection_name=self.collection_name, data=rows)
        return int(result.get("insert_count", len(rows))) if isinstance(result, dict) else len(rows)

    def flush_and_load(self) -> None:
        self._ensure_connected()
        self.client.flush(self.collection_name)
        self.client.load_collection(self.collection_name)

    def search(
        self,
        vector: list[float],
        top_k: int,
        doc_type: str | None = None,
        population: str | None = None,
        symptom: str | None = None,
    ) -> list[dict[str, Any]]:
        self._ensure_connected()
        if not self.has_collection():
            raise RuntimeError(f"鎼滅储澶辫触锛歝ollection 涓嶅瓨鍦? {self.collection_name}")
        self.client.load_collection(self.collection_name)
        filters: list[str] = []
        if doc_type:
            filters.append(f'doc_type == "{doc_type}"')
        if population:
            filters.append(f'applicable_population like "%{population}%"')
        if symptom:
            filters.append(f'related_symptoms like "%{symptom}%"')
        filter_expr = " and ".join(filters) if filters else ""
        return self.client.search(
            collection_name=self.collection_name,
            data=[vector],
            anns_field="embedding",
            limit=top_k,
            filter=filter_expr,
            output_fields=MILVUS_OUTPUT_FIELDS,
            search_params={"metric_type": "COSINE", "params": {}},
        )[0]

    def _ensure_connected(self) -> None:
        if self.client is None:
            raise RuntimeError("Milvus client is not connected. Call connect() first.")


def json_quote(value: str) -> str:
    import json

    return json.dumps(value, ensure_ascii=False)
