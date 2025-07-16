class ObservabilityClient {
    private val instrumentationManager: InstrumentationManager

    constructor(
        sdkKey: String,
        resource: Resource
    ) {
        this.instrumentationManager = InstrumentationManager(resource)
    }
}