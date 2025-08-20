using System.Net.Http;
using System.Threading;
using System.Threading.Tasks;

namespace LaunchDarkly.Observability.Sampling
{
    /// <summary>
    /// Minimal wrapper around HttpClient that implements IHttpClient
    /// </summary>
    internal class HttpClientWrapper : IHttpClient, System.IDisposable
    {
        private readonly HttpClient _httpClient;

        /// <summary>
        /// Initializes a new instance of the HttpClientWrapper
        /// </summary>
        /// <param name="httpClient">The HttpClient instance to wrap</param>
        public HttpClientWrapper(HttpClient httpClient)
        {
            _httpClient = httpClient ?? throw new System.ArgumentNullException(nameof(httpClient));
        }

        /// <inheritdoc />
        public Task<HttpResponseMessage> PostAsync(string requestUri, HttpContent content,
            CancellationToken cancellationToken = default)
        {
            return _httpClient.PostAsync(requestUri, content, cancellationToken);
        }

        /// <summary>
        /// Releases all resources used by the HttpClientWrapper
        /// </summary>
        public void Dispose()
        {
            _httpClient?.Dispose();
        }
    }
}
