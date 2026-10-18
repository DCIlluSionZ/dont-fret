FROM node:22-alpine AS builder
WORKDIR /web
COPY web/package*.json ./
RUN npm install
COPY web/ ./
ARG ANALYSIS_API_ORIGIN=http://analysis:8000
ENV ANALYSIS_API_ORIGIN=$ANALYSIS_API_ORIGIN
RUN npm run build
FROM node:22-alpine
WORKDIR /web
ENV NODE_ENV=production
COPY --from=builder /web /web
RUN addgroup -S dontfret && adduser -S -G dontfret dontfret && chown -R dontfret:dontfret /web
USER dontfret
EXPOSE 3000
HEALTHCHECK --interval=30s --timeout=5s --start-period=15s --retries=3 CMD node -e "fetch('http://127.0.0.1:3000/').then(r=>process.exit(r.ok?0:1)).catch(()=>process.exit(1))"
CMD ["npm","run","start"]
