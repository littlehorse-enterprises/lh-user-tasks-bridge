FROM ghcr.io/littlehorse-enterprises/alpine-nginx-nodejs/nginx-nodejs:main
WORKDIR /app

# The standalone output keeps the workspace layout: the app lives in console/ and
# console/node_modules/next is a relative symlink into the pnpm store beside it.
# Copy the tree as-is so that link still resolves, then point the base image's
# `node /app/server.js` at the real entrypoint.
COPY ./.next/standalone ./
COPY ./.next/static ./console/.next/static
RUN ln -s /app/console/server.js /app/server.js

COPY ./entrypoint.sh ./

ENV PORT=3000
ENV HOSTNAME=0.0.0.0
ENV NODE_ENV=production

EXPOSE 3000

ENTRYPOINT [ "./entrypoint.sh" ]
