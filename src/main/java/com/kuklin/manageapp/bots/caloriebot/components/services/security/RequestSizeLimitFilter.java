package com.kuklin.manageapp.bots.caloriebot.components.services.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kuklin.manageapp.bots.caloriebot.configurations.AiInputLimitsProperties;
import com.kuklin.manageapp.bots.caloriebot.models.exceptions.ErrorResponse;
import com.kuklin.manageapp.bots.caloriebot.models.exceptions.ErrorResponseException;
import com.kuklin.manageapp.bots.caloriebot.models.exceptions.ErrorStatus;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * Ограничивает размер тела любого запроса (calorie.ai-input.request-max-size).
 * Tomcat и Spring сами размер JSON-тела не ограничивают.
 * Если Content-Length известен — отвечаем 413 сразу, не читая тело.
 * Если нет (chunked) — тело читается через счётчик, и при превышении чтение обрывается
 * {@link RequestTooLargeException}, которую GlobalExceptionHandler превращает в 413.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RequestSizeLimitFilter extends OncePerRequestFilter {

    private final AiInputLimitsProperties limits;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        long maxBytes = limits.getRequestMaxSize().toBytes();
        long contentLength = request.getContentLengthLong();

        if (contentLength > maxBytes) {
            log.warn("[REQUEST_SIZE_LIMIT] {} {} -> {} bytes, limit {}",
                    request.getMethod(), request.getRequestURI(), contentLength, maxBytes);
            writeTooLarge(request, response);
            return;
        }

        filterChain.doFilter(contentLength >= 0 ? request : new LimitedBodyRequest(request, maxBytes), response);
    }

    private void writeTooLarge(HttpServletRequest request, HttpServletResponse response) throws IOException {
        ErrorResponseException e = new ErrorResponseException(ErrorStatus.REQUEST_TOO_LARGE, tooLargeMessage(limits));
        response.setStatus(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getOutputStream(), new ErrorResponse(e, new ServletWebRequest(request)));
    }

    public static String tooLargeMessage(AiInputLimitsProperties limits) {
        return String.format("Request is too large. Maximum is %d MB.", limits.getRequestMaxSize().toMegabytes());
    }

    /** Тело запроса оказалось больше лимита — бросается при чтении тела без Content-Length. */
    public static class RequestTooLargeException extends IOException {
        public RequestTooLargeException() {
            super("Request body exceeds the size limit");
        }
    }

    private static class LimitedBodyRequest extends HttpServletRequestWrapper {
        private final long maxBytes;
        private ServletInputStream inputStream;

        LimitedBodyRequest(HttpServletRequest request, long maxBytes) {
            super(request);
            this.maxBytes = maxBytes;
        }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            if (inputStream == null) {
                inputStream = new LimitedInputStream(super.getInputStream(), maxBytes);
            }
            return inputStream;
        }

        @Override
        public BufferedReader getReader() throws IOException {
            String encoding = getCharacterEncoding();
            Charset charset = encoding != null ? Charset.forName(encoding) : StandardCharsets.UTF_8;
            return new BufferedReader(new InputStreamReader(getInputStream(), charset));
        }
    }

    private static class LimitedInputStream extends ServletInputStream {
        private final ServletInputStream delegate;
        private long remaining;

        LimitedInputStream(ServletInputStream delegate, long maxBytes) {
            this.delegate = delegate;
            this.remaining = maxBytes;
        }

        @Override
        public int read() throws IOException {
            int b = delegate.read();
            if (b != -1 && --remaining < 0) {
                throw new RequestTooLargeException();
            }
            return b;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            int n = delegate.read(b, off, len);
            if (n > 0 && (remaining -= n) < 0) {
                throw new RequestTooLargeException();
            }
            return n;
        }

        @Override
        public boolean isFinished() {
            return delegate.isFinished();
        }

        @Override
        public boolean isReady() {
            return delegate.isReady();
        }

        @Override
        public void setReadListener(ReadListener readListener) {
            delegate.setReadListener(readListener);
        }
    }
}
