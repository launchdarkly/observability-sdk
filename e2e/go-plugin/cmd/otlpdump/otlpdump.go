// Command otlpdump is an OTLP endpoint that prints the exceptions it receives,
// including the source context attached to every frame. It stands in for the
// backend while working on how errors are recorded: what reaches it is what the
// backend would have parsed, so a change can be checked without a stack running
// and without waiting for the data to appear in the UI.
package main

import (
	"bytes"
	"compress/gzip"
	"encoding/json"
	"flag"
	"fmt"
	"io"
	"log"
	"net/http"
	"strings"

	collectortrace "go.opentelemetry.io/proto/otlp/collector/trace/v1"
	commonpb "go.opentelemetry.io/proto/otlp/common/v1"
	"google.golang.org/protobuf/proto"
)

// structuredFrame is a frame of exception.structured_stacktrace as it arrives on
// the wire, which is the shape the backend reads.
type structuredFrame struct {
	FileName     string `json:"fileName"`
	LineNumber   int    `json:"lineNumber"`
	FunctionName string `json:"functionName"`
	Error        string `json:"error"`
	LinesBefore  string `json:"linesBefore"`
	LineContent  string `json:"lineContent"`
	LinesAfter   string `json:"linesAfter"`
}

func main() {
	addr := flag.String("addr", ":4318", "address to receive OTLP over HTTP on")
	text := flag.Bool("text", false, "also print the plain text stack trace of each exception")
	flag.Parse()

	http.HandleFunc("/v1/traces", func(w http.ResponseWriter, r *http.Request) {
		respondEmpty(w)
		spans, err := readTraces(r)
		if err != nil {
			log.Printf("could not read export: %v", err)
			return
		}
		printExceptions(spans, *text)
	})
	// Logs and metrics arrive on their own paths and are not what this tool is
	// for, but they have to be accepted or the SDK retries them forever.
	http.HandleFunc("/", func(w http.ResponseWriter, r *http.Request) {
		_, _ = io.Copy(io.Discard, r.Body)
		respondEmpty(w)
	})

	log.Printf("receiving OTLP over HTTP on %s", *addr)
	log.Fatal(http.ListenAndServe(*addr, nil))
}

func respondEmpty(w http.ResponseWriter) {
	w.Header().Set("Content-Type", "application/x-protobuf")
	w.WriteHeader(http.StatusOK)
}

func readTraces(r *http.Request) (*collectortrace.ExportTraceServiceRequest, error) {
	body, err := io.ReadAll(r.Body)
	if err != nil {
		return nil, err
	}
	if r.Header.Get("Content-Encoding") == "gzip" {
		reader, err := gzip.NewReader(bytes.NewReader(body))
		if err != nil {
			return nil, err
		}
		if body, err = io.ReadAll(reader); err != nil {
			return nil, err
		}
	}
	export := &collectortrace.ExportTraceServiceRequest{}
	if err := proto.Unmarshal(body, export); err != nil {
		return nil, err
	}
	return export, nil
}

func printExceptions(export *collectortrace.ExportTraceServiceRequest, withText bool) {
	for _, resource := range export.ResourceSpans {
		for _, scope := range resource.ScopeSpans {
			for _, span := range scope.Spans {
				for _, event := range span.Events {
					if event.Name != "exception" {
						continue
					}
					printException(span.Name, event.Attributes, withText)
				}
			}
		}
	}
}

func printException(spanName string, attributes []*commonpb.KeyValue, withText bool) {
	fmt.Printf("\n===== exception on span %q =====\n", spanName)

	var structured string
	for _, attr := range attributes {
		value := attr.Value.GetStringValue()
		switch attr.Key {
		case "exception.structured_stacktrace":
			structured = value
		case "exception.stacktrace":
			if withText {
				fmt.Printf("--- exception.stacktrace ---\n%s\n", value)
			}
		default:
			fmt.Printf("%s = %s\n", attr.Key, value)
		}
	}

	if structured == "" {
		fmt.Println("no exception.structured_stacktrace: the backend will parse the text stack trace instead")
		return
	}
	var frames []structuredFrame
	if err := json.Unmarshal([]byte(structured), &frames); err != nil {
		log.Printf("could not read frames: %v", err)
		return
	}

	fmt.Printf("--- %d frames ---\n", len(frames))
	for i, frame := range frames {
		fmt.Printf("[%d] %s in %s at line %d\n", i, frame.FunctionName, frame.FileName, frame.LineNumber)
		if frame.LineContent == "" {
			fmt.Println("       (no source for this frame on this host)")
			continue
		}
		for _, line := range splitLines(frame.LinesBefore) {
			fmt.Printf("       | %s\n", line)
		}
		fmt.Printf("    >> | %s\n", frame.LineContent)
		for _, line := range splitLines(frame.LinesAfter) {
			fmt.Printf("       | %s\n", line)
		}
	}
}

func splitLines(lines string) []string {
	if lines == "" {
		return nil
	}
	return strings.Split(lines, "\n")
}
