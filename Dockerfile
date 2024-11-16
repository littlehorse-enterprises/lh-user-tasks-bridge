FROM node:alpine
WORKDIR /app

COPY ./ui/.next/standalone ./
COPY ./ui/.next/static ./ui/.next/static
COPY ./node_modules ./node_modules
COPY ./entrypoint.sh ./

ENV NODE_ENV=production \
    PORT=3000 \
    HOSTNAME="0.0.0.0"
EXPOSE 3000

ENTRYPOINT [ "./entrypoint.sh" ]
