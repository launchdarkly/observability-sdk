# frozen_string_literal: true

require 'json'

module LaunchDarklyObservability
  module SourceContext
    CONTEXT_LINES = 4
    MAX_FRAMES = 20
    MAX_LINE_LENGTH = 1000
    BACKTRACE_LINE_PATTERN = /^(.+):(\d+)(?::in `(.+?)')?$/

    @file_cache = {}

    module_function

    def build_structured_stacktrace(exception)
      return nil unless exception

      backtrace = exception.backtrace
      return nil unless backtrace&.any?

      error_message = begin
        exception.full_message(highlight: false, order: :top).to_s.lines.first&.chomp
      rescue StandardError
        exception.message
      end
      frames = backtrace.first(MAX_FRAMES).filter_map do |backtrace_line|
        build_frame(backtrace_line, error_message)
      end

      frames.empty? ? nil : frames
    rescue StandardError
      nil
    end

    def read_source_context(file_name, line_number)
      return nil unless file_name && line_number
      return nil unless File.exist?(file_name) && File.readable?(file_name)

      source_lines = cached_source_lines(file_name)
      return nil unless source_lines
      return nil if line_number <= 0 || line_number > source_lines.length

      target_index = line_number - 1
      before_start = [target_index - CONTEXT_LINES, 0].max
      before_lines = source_lines[before_start...target_index] || []
      after_end = [target_index + CONTEXT_LINES, source_lines.length - 1].min
      after_lines = source_lines[(target_index + 1)..after_end] || []

      {
        lineContent: source_lines[target_index],
        linesBefore: before_lines.empty? ? nil : before_lines.join("\n"),
        linesAfter: after_lines.empty? ? nil : after_lines.join("\n")
      }
    rescue StandardError
      nil
    end

    def build_frame(backtrace_line, error_message)
      matches = BACKTRACE_LINE_PATTERN.match(backtrace_line)
      return nil unless matches

      file_name = matches[1]
      line_number = matches[2].to_i
      function_name = matches[3]

      frame = {
        fileName: file_name,
        lineNumber: line_number,
        error: error_message
      }
      frame[:functionName] = function_name if function_name

      source_context = read_source_context(file_name, line_number)
      if source_context
        frame[:lineContent] = source_context[:lineContent]
        frame[:linesBefore] = source_context[:linesBefore] if source_context[:linesBefore]
        frame[:linesAfter] = source_context[:linesAfter] if source_context[:linesAfter]
      end

      frame
    end
    private_class_method :build_frame

    def cached_source_lines(file_name)
      return @file_cache[file_name] if @file_cache.key?(file_name)

      lines = File.readlines(file_name, chomp: true).map do |line|
        line.length > MAX_LINE_LENGTH ? line[0...MAX_LINE_LENGTH] : line
      end
      @file_cache[file_name] = lines
      lines
    rescue StandardError
      @file_cache[file_name] = nil
      nil
    end
    private_class_method :cached_source_lines
  end
end
