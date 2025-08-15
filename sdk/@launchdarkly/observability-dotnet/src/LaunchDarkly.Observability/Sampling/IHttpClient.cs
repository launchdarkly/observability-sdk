using System.Net.Http;
using System.Threading;
using System.Threading.Tasks;

namespace LaunchDarkly.Observability.Sampling
{
    /// <summary>
    /// Interface for HTTP client operations used by SamplingConfigClient
    /// </summary>
    internal interface IHttpClient
    {
        /// <summary>
        /// Sends a POST request to the specified URI with the provided content
        /// </summary>
        /// <param name="requestUri">The URI to send the request to</param>
        /// <param name="content">The HTTP content to send</param>
        /// <param name="cancellationToken">Cancellation token for the operation</param>
        /// <returns>The HTTP response message</returns>
        Task<HttpResponseMessage> PostAsync(string requestUri, HttpContent content,
            CancellationToken cancellationToken = default);
    }
}
