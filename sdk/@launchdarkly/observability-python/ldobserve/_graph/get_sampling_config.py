import logging
from typing import Callable
import threading
import asyncio

from ldobserve._graph.generated.public_graph_client.get_sampling_config import (
    GetSamplingConfigSampling,
)
from .generated.public_graph_client import Client
from .generated.public_graph_client.custom_queries import Query

tasks = set()


def get_sampling_config(
    endpoint: str,
    project_id: str,
    callback: Callable[[GetSamplingConfigSampling], None],
):
    client = Client(url=endpoint)

    async def _get_sampling_config():
        result = await client.get_sampling_config(project_id)
        logging.getLogger(__name__).debug("Got sampling config: %s", result)
        callback(result.sampling)

    is_async = False
    try:
        # Get the running event loop to check if we're in an async context.
        # If we are not, then this will raise a RuntimeError.
        asyncio.get_running_loop()
        task = asyncio.create_task(_get_sampling_config())
        tasks.add(task)
        task.add_done_callback(lambda task: tasks.remove(task))
        is_async = True
        logging.getLogger(__name__).debug("Running in existing asyncio event loop.")
    except RuntimeError:
        pass

    if not is_async:
        # We were not in an async context, so we need to run the task in a thread.
        logging.getLogger(__name__).debug(
            "No running event loop found, running in thread."
        )
        thread = threading.Thread(target=lambda: asyncio.run(_get_sampling_config()))
        thread.start()
