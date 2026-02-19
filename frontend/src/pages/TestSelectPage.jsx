import { useEffect, useState } from "react";

export default function TestSelectPage() {
  const [rows, setRows] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  const load = async () => {
    try {
      setLoading(true);
      setError("");

      // ✅ 상대경로 호출: (dev) Vite proxy 사용 시 백엔드로 전달됨
      const res = await fetch("/api/test/selectTest", {
        method: "GET",
        headers: { "Accept": "application/json" },
      });

      if (!res.ok) {
        const text = await res.text().catch(() => "");
        throw new Error(`HTTP ${res.status} ${res.statusText}${text ? ` - ${text}` : ""}`);
      }

      const data = await res.json();

      // ✅ 서버가 배열(List<TestDTO>)로 반환한다고 가정
      if (!Array.isArray(data)) {
        throw new Error("응답 형식이 배열이 아닙니다. (List<TestDTO> JSON 배열을 기대)");
      }

      setRows(data);
    } catch (e) {
      setError(e?.message || "알 수 없는 오류");
      setRows([]);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    load();
  }, []);

  return (
    <div style={{ padding: 16 }}>
      <h2 style={{ marginBottom: 8 }}>/api/test/selectTest 결과</h2>

      <div style={{ display: "flex", gap: 8, alignItems: "center", marginBottom: 12 }}>
        <button onClick={load} disabled={loading}>
          {loading ? "불러오는 중..." : "새로고침"}
        </button>
        <span style={{ opacity: 0.75 }}>총 {rows.length}건</span>
      </div>

      {error && (
        <div style={{ marginBottom: 12, padding: 12, border: "1px solid #f5c2c7", background: "#f8d7da" }}>
          <strong>에러:</strong> {error}
          <div style={{ marginTop: 6, opacity: 0.8 }}>
            - 백엔드가 실행 중인지, 프록시 설정(vite.config.js)이 맞는지 확인하세요.
          </div>
        </div>
      )}

      {loading ? (
        <p>Loading...</p>
      ) : rows.length === 0 ? (
        <p>데이터가 없습니다.</p>
      ) : (
        <div style={{ overflowX: "auto" }}>
          <table style={{ width: "100%", borderCollapse: "collapse", minWidth: 520 }}>
            <thead>
              <tr>
                <th style={th}>id</th>
                <th style={th}>content</th>
                <th style={th}>created_at</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((r, idx) => (
                <tr key={`${r.id}-${idx}`}>
                  <td style={td}>{r.id}</td>
                  <td style={td}>{r.content}</td>
                  <td style={td}>{r.created_at}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      <div style={{ marginTop: 10, fontSize: 12, opacity: 0.7 }}>
        ※ 만약 JSON 필드명이 <code>createdAt</code>으로 내려온다면, 화면에서 <code>r.created_at</code>을
        <code> r.createdAt</code>으로 바꾸면 됩니다.
      </div>
    </div>
  );
}

const th = {
  textAlign: "left",
  borderBottom: "1px solid #ddd",
  padding: "10px 8px",
  background: "#f7f7f7",
};

const td = {
  borderBottom: "1px solid #eee",
  padding: "10px 8px",
  verticalAlign: "top",
};
