# Privately self-hosting Time to Chang

You can run the entire application on your home server and access it from your laptop or phone, including away from home, without publishing a domain or forwarding router ports. My recommendation is **Docker Compose on the server, with access through Tailscale**, a private network joining only your authorized devices.

This guide assumes a Linux server. It describes the changes to make when deploying; it does not change the repository's existing configuration.

## What “no open ports” means here

No application ports need to accept connections from the public internet. The application still needs a local listener, and authorized devices connect through a private network. Literally having no listening sockets anywhere would prevent remote access.

Tailscale normally works without inbound firewall rules or port forwarding. It uses outbound connectivity and NAT traversal; when a direct connection is unavailable, it can relay traffic. Keep outbound connectivity available according to the [Tailscale firewall documentation](https://tailscale.com/docs/reference/faq/firewall-ports).

Relayed traffic remains end-to-end encrypted with WireGuard. Your application and PostgreSQL data stay on your server, but Tailscale's coordination service, and potentially its relays, are external dependencies. This is private application hosting, not a completely independent or offline network. See [Tailscale connection types](https://tailscale.com/docs/reference/connection-types).

The proposed path is:

```text
Your phone/laptop, connected to Tailscale
    |
    | encrypted private connection
    v
Home server: Tailscale Serve, private TCP port 80
    |
    v
127.0.0.1:8080 → nginx / Angular
                     |
                     | /api, internal Docker network
                     v
                 Spring Boot → PostgreSQL → persistent volume
```

You do not need a public IP address, dynamic DNS, a purchased domain, a subnet router, or an exit node for this setup. Use the server's numeric Tailscale address in your browser.

## 1. Prepare the server

You need:

- A Linux server that stays on, with persistent storage and internet access.
- A maintained Docker Engine release and Docker Compose v2.
- This repository on the server, including `compose.yaml`, `frontend/`, and `backend/`.
- Tailscale installed on the **server host**, and on every phone or computer that will access the site.
- A backup destination outside the server.

Docker builds the Angular frontend and Java backend; you do not need host installations of Node.js, Java, Maven, or PostgreSQL. Follow the [README](README.md) for the application's full initialization and recovery instructions.

Use Docker Engine 28 or later: older releases had a caveat allowing machines on the same network segment to reach some ports published to localhost. See [Docker's port publishing documentation](https://docs.docker.com/engine/network/port-publishing/).

## 2. Bind the application to localhost

The current `compose.yaml` publishes `${PORT:-8080}:80`, which binds to all host interfaces by default. Before starting the server deployment, **replace** the `web` service's `ports` list with:

```yaml
    ports:
      - "127.0.0.1:${PORT:-8080}:80"
```

Keep the rest of that service unchanged. Replace the existing mapping rather than adding another mapping or an override that might retain it. This makes the published web port accessible through the server's loopback interface. [Docker documents the difference between all-interface and localhost publishing](https://docs.docker.com/engine/network/port-publishing/).

Keep `api` and `db` without published ports, as they already are. Do not use `compose.dev.yaml` for this deployment.

For a fresh installation, copy `.env.example` to `.env`, then configure:

```dotenv
PORT=8080
DB_PASSWORD=replace-with-your-own-long-random-password
SECURE_COOKIE=false
```

Keep an existing `.env` if you are restoring or moving an installation. Changing `DB_PASSWORD` does not change the password inside an existing PostgreSQL volume.

`SECURE_COOKIE=false` is intentional for the private HTTP address used below. Setting it to `true` while browsing over HTTP prevents the session cookie from working.

From the repository root, inspect the resolved configuration and start the application:

```sh
docker compose config
docker compose up --build -d
docker compose ps
curl --fail http://127.0.0.1:8080/api/health
```

In the resolved web port configuration, check that `host_ip` is `127.0.0.1`. Configuration output contains the database password; do not publish it. The health endpoint should return `{"status":"UP"}` once startup finishes.

PostgreSQL initializes its empty volume automatically, and Spring Boot runs the Flyway SQL migrations. Do not execute the SQL files manually. If using a different `PORT`, change `8080` in the health check and proxy command below too.

## 3. Join your private network

Install Tailscale using its [official downloads and platform instructions](https://tailscale.com/download). On the Linux server, connect it to your account:

```sh
sudo tailscale up
tailscale ip -4
```

Complete authentication using the link printed by the command. Connect your laptop and phone to the same private network, called a *tailnet*. Other people should use their own invited identities, not your account credentials.

Protect the identity account with multifactor authentication. In the Tailscale administration console, configure access rules so only your intended users can reach this server on **TCP port 80**. Broad existing allow rules must be removed or narrowed: adding a restrictive grant does not cancel another grant that already allows access. Keep any required server administration access separately. Follow the [Tailscale grants documentation](https://tailscale.com/docs/features/access-control/grants).

Keep unsolicited inbound internet traffic blocked on the router for both IPv4 and IPv6. Do not create port-forwarding rules or a router “DMZ” entry. If you also want to prevent automatic router port mappings, disable UPnP/NAT-PMP port mapping on the router; relay connectivity is the fallback when direct connectivity is unavailable.

## 4. Make the site available only through Tailscale

On the server, run:

```sh
sudo tailscale serve --bg --http=80 http://127.0.0.1:8080
tailscale serve status
tailscale ip -4
```

This uses **Tailscale Serve**, which publishes the local service within your tailnet. The explicit `--http=80` selects HTTP rather than the default HTTPS behavior; `--bg` runs the service in the background. See the [Serve command reference](https://tailscale.com/docs/reference/tailscale-cli/serve).

On an authorized device with Tailscale connected, open:

```text
http://YOUR_SERVER_TAILSCALE_IP/
```

For example, if `tailscale ip -4` prints `100.101.102.103`, open `http://100.101.102.103/`. Use your actual address. You do not need to configure public DNS or enable MagicDNS to use this numeric address.

Do **not** enable Tailscale Funnel: [Funnel exposes a service to the public internet](https://tailscale.com/docs/features/tailscale-funnel).

Create an application account and log in normally. Angular already uses same-origin `/api` requests, and nginx forwards them internally, so no frontend API URL or CORS changes are necessary.

The browser uses HTTP, but traffic between your devices travels inside the encrypted Tailscale connection. This does not provide browser-level TLS: the browser may display a “not secure” indicator and restrict features that require an HTTPS secure context. Keep the HTTP endpoint confined to this private path. Tailscale explains this distinction in its [HTTPS documentation](https://tailscale.com/docs/how-to/set-up-https-certificates).

## 5. Verify the boundary

Check all of the following before relying on the setup:

| Check | Expected result |
|---|---|
| `docker compose ps` on the server | Web published as `127.0.0.1:8080->80/tcp`; no host mapping for API or PostgreSQL |
| Server requests `http://127.0.0.1:8080/api/health` | Healthy response |
| Another home-network device without Tailscale requests `http://SERVER_LAN_IP:8080` | Connection fails |
| Authorized phone on mobile data, Wi-Fi off, Tailscale on | Site opens using the Tailscale IP; login and saving a workout work |
| Same phone with Tailscale disconnected, making a fresh request | Site cannot be reached |
| A tailnet user excluded by your access rules | Cannot reach the site |
| Router configuration | No application port forwarding, DMZ exposure, or unsolicited IPv6 inbound access |

If an unauthorized connection succeeds, inspect existing Tailscale rules and Docker port mappings before continuing. A missing public DNS record alone is not an access control.

## Day-to-day use and maintenance

- Ensure Docker and the Tailscale daemon start on boot. Compose already gives each service `restart: unless-stopped`. Reboot once and verify both application health and private access.
- Monitor Tailscale device authentication expiry so the unattended server does not unexpectedly lose connectivity. Revoke lost phones and unused devices promptly.
- Each additional person needs both private-network permission and a Time to Chang account. Adding someone to a shared WOD does not grant network access or create their application account.
- Back up PostgreSQL and preserve `.env` separately. Follow the [README backup and restore procedure](README.md#optional-back-up-and-restore), copy backups off the server, and test restoration. A Docker volume is persistent storage, not a backup.
- Keep the Compose project name consistent to reuse the correct database volume. Never use `docker compose down --volumes` for routine maintenance: it deletes the database volume.
- Back up before updates, then rebuild using `docker compose up --build -d`. Recheck the localhost binding after merging changes to `compose.yaml`.
- Update the server OS, Docker, and Tailscale. The app currently has no password reset or email verification; preserve account credentials accordingly.

The nginx authentication rate limit currently uses the immediate proxy IP. With Serve in front, multiple users may share that limit; repeated simultaneous login attempts can therefore receive HTTP 429. This does not affect the ownership of their application data.

## Alternatives and tradeoffs

### Access only while at home

If you never need remote access, you can omit Tailscale and publish web port 8080 on the server's fixed **LAN address** instead, for example `192.168.1.50:8080:80`. Reserve that address on the router, restrict access to trusted LAN clients, and keep public inbound traffic blocked. Open `http://192.168.1.50:8080` at home.

This exposes a port to the LAN and sends HTTP over the LAN without the VPN encryption. If you also want to keep the application unavailable to other LAN devices, use the main localhost-plus-Tailscale setup.

### Private HTTPS

Tailscale Serve can also provide HTTPS while keeping access private. However, its publicly trusted certificates publish the machine's fully qualified domain name in public Certificate Transparency logs. That does not make the application publicly reachable, but it conflicts with a strict requirement to publish no service name. This is why the main recipe uses a numeric IP and HTTP inside the VPN. See [Tailscale's certificate privacy explanation](https://tailscale.com/docs/how-to/set-up-https-certificates).

If you accept that metadata exposure, configure Serve HTTPS, remove the HTTP listener, permit private TCP 443, and set `SECURE_COOKIE=true`. If you need HTTPS without public certificate metadata, a reverse proxy using a private certificate authority is another option, but you must install and trust that CA on every client and manage certificate renewal.

### No third-party network service at all

The application stack is fully hosted at home with the recommended setup; the network coordination is not. A conventional self-hosted VPN usually needs a reachable inbound endpoint. Avoiding home inbound ports generally means connecting outward to a reachable relay or another server you control, which adds infrastructure elsewhere. If you reject both inbound reachability and any outside relay/coordination infrastructure, limit access to your home network.
