import { useEffect, useMemo, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { AnalysisDetail, downloadReport, fetchAnalysis } from '../api/client';

function AnalysisDetailPage() {
  const { id } = useParams();
  const navigate = useNavigate();
  const [analysis, setAnalysis] = useState<AnalysisDetail | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [downloading, setDownloading] = useState<'pdf' | 'excel' | null>(null);

  const summary = useMemo(() => {
    if (!analysis) return [];
    const dependencyScopeLabel =
      analysis.dependencyScope === 'SPRING_ONLY' ? 'Dépendances Spring uniquement' : 'Toutes les dépendances';
    return [
      { label: 'Statut', value: analysis.status },
      { label: 'Portée des dépendances', value: dependencyScopeLabel },
      { label: 'Workpoints', value: analysis.totalWorkpoints },
      { label: 'Spring courant', value: analysis.springVersionCurrent },
      { label: 'Spring cible', value: analysis.springVersionTarget }
    ];
  }, [analysis]);

  useEffect(() => {
    if (!id) return;
    const load = async () => {
      try {
        const data = await fetchAnalysis(id);
        setAnalysis(data);
      } catch (err) {
        console.error(err);
        setError("Impossible de charger l'analyse demandée");
      } finally {
        setLoading(false);
      }
    };

    load();
  }, [id]);

  const handleDownload = async (format: 'pdf' | 'excel') => {
    if (!id) return;
    setDownloading(format);

    try {
      const data = await downloadReport(id, format);
      let blob: Blob;

      if (data instanceof Blob) {
        blob = data;
      } else {
        const json = typeof data === 'string' ? data : JSON.stringify(data, null, 2);
        blob = new Blob([json], { type: 'application/json' });
      }

      const url = window.URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      link.download = `analysis-${id}.${format === 'pdf' ? 'pdf' : 'json'}`;
      document.body.appendChild(link);
      link.click();
      link.remove();
      window.URL.revokeObjectURL(url);
    } catch (err) {
      console.error(err);
      setError('Téléchargement impossible pour le moment.');
    } finally {
      setDownloading(null);
    }
  };

  if (!id) {
    return (
      <div className="card">
        <p className="error">Aucun identifiant fourni.</p>
        <button onClick={() => navigate('/analyses')}>Retour à la liste</button>
      </div>
    );
  }

  return (
    <div className="card">
      <h1>Détail de l'analyse</h1>
      {loading && <p className="loading">Chargement...</p>}
      {error && <p className="error">{error}</p>}
      {!loading && analysis && (
        <>
          <div className="summary-grid">
            {summary.map((item) => (
              <div key={item.label} className="summary-card">
                <span>{item.label}</span>
                <strong>{item.value}</strong>
              </div>
            ))}
          </div>

          <div className="actions">
            <button onClick={() => handleDownload('pdf')} disabled={!!downloading}>
              {downloading === 'pdf' ? 'Téléchargement...' : 'Télécharger en PDF'}
            </button>
            <button onClick={() => handleDownload('excel')} disabled={!!downloading}>
              {downloading === 'excel' ? 'Téléchargement...' : 'Télécharger en Excel (JSON)'}
            </button>
          </div>

          <div className="table-wrapper">
            <table>
              <thead>
                <tr>
                  <th>Type</th>
                  <th>Sévérité</th>
                  <th>Titre</th>
                  <th>Workpoints</th>
                </tr>
              </thead>
              <tbody>
                {analysis.changes.map((change, index) => (
                  <tr key={`${change.title}-${index}`}>
                    <td>{change.type}</td>
                    <td>{change.severity}</td>
                    <td>{change.title}</td>
                    <td>{change.workpoints}</td>
                  </tr>
                ))}
                {analysis.changes.length === 0 && (
                  <tr>
                    <td colSpan={4} style={{ textAlign: 'center', padding: '1rem' }}>
                      Aucun changement détecté.
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>
        </>
      )}
    </div>
  );
}

export default AnalysisDetailPage;
