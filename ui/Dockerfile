FROM ghcr.io/littlehorse-enterprises/alpine-nginx-nodejs/nginx-nodejs:main AS runner
ENV NODE_ENV=production

WORKDIR /app
RUN mkdir -p /app/.next

COPY ./entrypoint.sh ./
COPY ./.next/standalone ./
COPY ./.next/static ./.next/static

EXPOSE 3000
ENV PORT=3000
ENV HOSTNAME="0.0.0.0"

ENTRYPOINT [ "./entrypoint.sh" ]
