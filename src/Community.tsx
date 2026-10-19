import { useEffect, useState } from "react";
import { Star, Check, Trash2 } from "lucide-react";
import { api } from "./client";
import { useApp, run } from "./state";
import { useResource, useText, TrackList } from "./ui";
import { play } from "./player";
import type { CommunityHome, CommunityRole } from "./communityTypes";

function useCommunity<T>(action: "role" | "home") {
  const account = useApp((s) => s.me?.id);
  const [revision, setRevision] = useState(0);
  useEffect(() => {
    const refresh = () => {
      if (!document.hidden) setRevision((v) => v + 1);
    };
    const timer = setInterval(refresh, action === "role" ? 60000 : 300000);
    window.addEventListener("focus", refresh);
    document.addEventListener("visibilitychange", refresh);
    return () => {
      clearInterval(timer);
      window.removeEventListener("focus", refresh);
      document.removeEventListener("visibilitychange", refresh);
    };
  }, [action]);
  return useResource(() => api.community<T>(action), [account, revision]);
}
export function EditorialButton({
  id,
  compact = false,
}: {
  id: number;
  compact?: boolean;
}) {
  const t = useText();
  const role = useCommunity<CommunityRole>("role");
  const [busy, setBusy] = useState(false);
  if (!role.value?.canEdit) return null;
  const picked = role.value.pickedIds.includes(id);
  const label = picked
    ? t("Убрать из выбора редакции", "Remove editorial pick")
    : t("В выбор редакции", "Add editorial pick");
  return (
    <button
      className={compact ? "icon-button" : ""}
      disabled={busy}
      aria-label={label}
      aria-pressed={picked}
      title={label}
      onClick={() => {
        setBusy(true);
        void run(async () => {
          await api.community("pick", { trackId: id, active: !picked });
          useApp.setState((s) => ({ revision: s.revision + 1 }));
        }).finally(() => setBusy(false));
      }}
    >
      {picked ? <Check size={20} /> : <Star size={20} />}
      {!compact && label}
    </button>
  );
}
export function CommunityHomeSection() {
  const t = useText(),
    data = useCommunity<CommunityHome>("home");
  const [period, setPeriod] = useState<"today" | "yesterday" | "week">("today");
  const [busy, setBusy] = useState(false);
  const rows = data.value?.[period] || [],
    picks = data.value?.editorial || [];
  const editablePicks = data.value?.role.canEdit
    ? picks.filter((r) => r.pickedByMe || data.value?.role.isOwner)
    : [];
  return (
    <section className="community-home" aria-busy={data.loading}>
      <h2>{t("Слушают в Veirune", "Popular in Veirune")}</h2>
      <div
        className="chips"
        role="group"
        aria-label={t("Период подборки", "Discovery period")}
      >
        {(
          [
            ["today", "Сегодня", "Today"],
            ["yesterday", "Вчера", "Yesterday"],
            ["week", "Неделя", "Week"],
          ] as const
        ).map(([key, ru, en]) => (
          <button
            key={key}
            aria-pressed={period === key}
            onClick={() => setPeriod(key)}
          >
            {t(ru, en)}
          </button>
        ))}
      </div>
      <p className="muted">{t("По числу слушателей", "By listeners")}</p>
      {data.error ? (
        <p role="status">
          {t("Не удалось загрузить подборки.", "Could not load collections.")}
        </p>
      ) : (
        <>
          {rows.length > 0 && (
            <TrackList
              tracks={rows.map((r) => r.track)}
              listenerCounts={Object.fromEntries(
                rows.map((r) => [r.trackId, r.listeners || 0]),
              )}
            />
          )}
          <div className="section-heading">
            <h2>{t("Выбор редакции", "Editors’ picks")}</h2>
            {picks.length > 0 && (
              <button
                onClick={() => void run(() => play(picks.map((r) => r.track)))}
              >
                {t("Слушать всё", "Play all")}
              </button>
            )}
          </div>
          {picks.length > 0 && <TrackList tracks={picks.map((r) => r.track)} />}
          {editablePicks.length > 0 && (
            <div className="editorial-manage">
              {editablePicks.map((r) => (
                <button
                  key={r.trackId}
                  disabled={busy}
                  onClick={() => {
                    setBusy(true);
                    void run(async () => {
                      await api.community("pick", {
                        trackId: r.trackId,
                        active: false,
                      });
                      useApp.setState((s) => ({ revision: s.revision + 1 }));
                    }).finally(() => setBusy(false));
                  }}
                >
                  <Trash2 size={18} />
                  {t("Убрать", "Remove")}: {r.track.title}
                </button>
              ))}
            </div>
          )}
        </>
      )}
    </section>
  );
}
export function EditorManagement() {
  const t = useText();
  const role = useCommunity<CommunityRole>("role");
  if (!role.value?.isOwner) return null;
  return <OwnerEditors />;
}
function OwnerEditors() {
  const t = useText();
  const data = useResource(
    () =>
      api.community<{
        users: {
          id: number;
          username: string;
          status: string;
          editor?: boolean;
        }[];
      }>("users"),
    [],
  );
  const me = useApp((s) => s.me?.id),
    [busy, setBusy] = useState(false);
  return (
    <section className="settings-card">
      <h2>{t("Управление пользователями", "User management")}</h2>
      <p>
        {t(
          "Редакторы выбирают музыку. Управление пользователями доступно только тебе.",
          "Editors choose music. User management stays exclusive to you.",
        )}
      </p>
      {data.error && (
        <p role="alert">
          {t("Не удалось загрузить пользователей.", "Could not load users.")}
        </p>
      )}
      {data.value?.users.map((user) => (
        <label className="setting-row" key={user.id}>
          <span>
            <strong>{user.username}</strong>
            <small>
              {user.id === me
                ? t("Владелец", "Owner")
                : user.editor
                  ? t("Редактор", "Editor")
                  : t("Слушатель", "Listener")}
            </small>
          </span>
          <input
            type="checkbox"
            aria-label={`${t("Редактор", "Editor")}: ${user.username}`}
            checked={user.id === me || user.editor === true}
            disabled={busy || user.id === me || user.status !== "approved"}
            onChange={(event) => {
              const active = event.target.checked;
              setBusy(true);
              void run(async () => {
                await api.community("editor", {
                  userId: user.id,
                  editor: active,
                });
                useApp.setState((s) => ({ revision: s.revision + 1 }));
              }).finally(() => setBusy(false));
            }}
          />
        </label>
      ))}
    </section>
  );
}
