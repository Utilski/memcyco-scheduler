import type { ReactNode } from "react";
import { Badge } from "../atoms/controls";

export function WorkspaceLayout({ children }: { children: ReactNode }) {
  return (
    <div className="app-shell">
      <aside className="sidebar">
        <a className="brand" href="#main">
          <span className="brand-mark">m.</span>
          <span>
            memcyco<span className="brand-subtitle">SCHEDULER</span>
          </span>
        </a>
        <div className="nav-label">WORKSPACE</div>
        <a className="nav-item active" href="#main">
          <span aria-hidden="true">▦</span> Schedulings
          <span className="nav-dot" />
        </a>
        <div className="sidebar-bottom">
          <span className="engine-dot" />
          Quartz engine
          <p>
            Persistent scheduling
            <br />
            RabbitMQ execution
          </p>
        </div>
      </aside>
      <div className="workspace">
        <header className="topbar">
          <span>
            Workspace <span className="breadcrumb-divider">/</span>
            <strong>Schedulings</strong>
          </span>
          <span className="workspace-label">
            LOCAL WORKSPACE <span className="avatar">M</span>
          </span>
        </header>
        <main id="main">
          {children}
          <footer className="page-footer">
            <span>Built for precise timing.</span>
            <Badge>QUARTZ + RABBITMQ</Badge>
          </footer>
        </main>
      </div>
    </div>
  );
}
