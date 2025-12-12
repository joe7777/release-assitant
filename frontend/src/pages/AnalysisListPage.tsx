import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Analysis, fetchAnalyses } from '../api/client';

function AnalysisListPage() {
  const navigate = useNavigate();
  const [analyses, setAnalyses] = useState<Analysis[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const load = async () => {
      try {
        const data = await fetchAnalyses();
        setAnalyses(data);
      } catch (err) {
        console.error(err);
        setError('Impossible de récupérer la liste des analyses');
      } finally {
        setLoading(false);
      }
    };

    load();
  }, []);

  return (
    <div className="card">
      <h1>Analyses</h1>
      {loading && <p className="loading">Chargement...</p>}
      {error && <p className="error">{error}</p>}
      {!loading && !error && (
        <div className="table-wrapper">
          <table>
            <thead>
              <tr>
                <th>ID</th>
                <th>Projet</th>
                <th>Spring courant</th>
                <th>Spring cible</th>
                <th>Statut</th>
                <th>Workpoints</th>
              </tr>
            </thead>
            <tbody>
              {analyses.map((analysis) => (
                <tr key={analysis.id} onClick={() => navigate(`/analyses/${analysis.id}`)} style={{ cursor: 'pointer' }}>
                  <td>{analysis.id}</td>
                  <td>{analysis.projectName}</td>
                  <td>{analysis.springVersionCurrent}</td>
                  <td>{analysis.springVersionTarget}</td>
                  <td>
                    <span className="status-badge">{analysis.status}</span>
                  </td>
                  <td>{analysis.totalWorkpoints}</td>
                </tr>
              ))}
              {analyses.length === 0 && (
                <tr>
                  <td colSpan={6} style={{ textAlign: 'center', padding: '1rem' }}>
                    Aucune analyse pour le moment.
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}

export default AnalysisListPage;
