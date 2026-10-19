# Remote browser desktop and NetBeans workflow

The VPS has a lightweight XFCE desktop available through noVNC. The browser gateway and the underlying VNC server listen on `127.0.0.1` only. This is intentional: do not open ports 5900 or 6080 publicly.

## Public HTTPS address

The configured public address is:

```text
https://desktop.techinn.co.zw/
```

It is protected by HTTP Basic Authentication and then by the noVNC session. The DNS provider must have an `A` record named `desktop` pointing to the VPS address before the hostname can reach this server. After DNS propagation, issue a trusted certificate with:

```bash
sudo certbot certonly --webroot -w /var/www/html \
  --cert-name desktop.techinn.co.zw -d desktop.techinn.co.zw \
  --agree-tos --non-interactive -m YOUR_EMAIL
sudo systemctl reload nginx
```

Until that certificate step succeeds, browsers may show the temporary certificate warning created by the hosting panel. Do not bypass that warning for a long-term setup; use the trusted certificate after the DNS record is live.

## Connect from a laptop browser

Create an SSH tunnel from the laptop that has the browser:

```bash
ssh -N -L 6080:127.0.0.1:6080 gzute5695@YOUR_VPS_HOST
```

Keep that terminal open, then browse to:

```text
http://127.0.0.1:6080/vnc.html?autoconnect=true&resize=remote
```

The page connects through the forwarded noVNC gateway to the private VNC display automatically. If using PuTTY, add a local port forward with source port `6080` and destination `127.0.0.1:6080` before opening the SSH session.

Service commands on the VPS:

```bash
sudo systemctl status hcs425-browser-desktop
sudo systemctl restart hcs425-browser-desktop
sudo systemctl stop hcs425-browser-desktop
```

The desktop is a separate virtual display at `:99` with a 1600x900 framebuffer. Logs are stored in `~/.config/hcs425/remote-desktop/`. xrdp is installed as an optional fallback but is disabled; the supported browser path is the localhost-only noVNC tunnel above.

## Start Apache NetBeans

NetBeans is installed at `/opt/netbeans`. From the XFCE terminal, run:

```bash
/opt/netbeans/bin/netbeans --jdkhome /usr/lib/jvm/java-21-openjdk-amd64
```

In NetBeans:

1. Select **File → Open Project**.
2. Choose `/home/gzu.techinn.co.zw/projects/hcs425-distributed-system`.
3. Allow Maven indexing and dependency resolution to finish.
4. Use the root project’s Maven actions for `verify`, `test`, and `package`.
5. For the desktop, open the built-in terminal and run `./scripts/start-desktop.sh`; the launcher handles the JavaFX module path and embedded server.

For a server-managed launch instead, use the VPS shell:

```bash
sudo systemctl start hcs425-netbeans
sudo systemctl status hcs425-netbeans
```

The IDE then appears on the same noVNC desktop. Stop it with `sudo systemctl stop hcs425-netbeans` before starting another copy manually.

The repeatable command-line checks are:

```bash
cd /home/gzu.techinn.co.zw/projects/hcs425-distributed-system
mvn clean verify
./scripts/run-benchmarks.sh
```

Use one terminal tab for the server, one for the desktop, and one for benchmarks or fault tests. The README contains the full command table and demo sequence.

## Cleanup later

The desktop setup was added only for VPS development and demonstration. To remove it later without touching the HCS425 source tree:

```bash
sudo systemctl disable --now hcs425-browser-desktop
sudo systemctl disable --now hcs425-netbeans
sudo rm -f /etc/systemd/system/hcs425-browser-desktop.service
sudo rm -f /etc/systemd/system/hcs425-netbeans.service
sudo rm -f /usr/local/bin/hcs425-browser-desktop
sudo systemctl daemon-reload
sudo apt purge xfce4 xfce4-goodies xrdp xorgxrdp x11vnc novnc websockify
sudo apt autoremove
```

Review the package list before confirming `apt autoremove`, because this VPS hosts unrelated services. `/opt/netbeans` and `/opt/netbeans-download` can be removed separately after confirming they are no longer needed.
