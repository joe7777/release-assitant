import { FormEvent, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { createAnalysis } from '../api/client';

const mockSpringTargets = ['6.0', '6.1', '6.2'];

function ProjectSelectionPage() {
  const navigate = useNavigate();
  const [projectGitUrl, setProjectGitUrl] = useState('');
  const [projectName, setProjectName] = useState('');
  const [branch, setBranch] = useState('main');
  const [springVersionTarget, setSpringVersionTarget] = useState(mockSpringTargets[0]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const isFormValid = useMemo(
    () => Boolean(projectGitUrl && projectName && branch && springVersionTarget),
    [projectGitUrl, projectName, branch, springVersionTarget]
  );

  const handleSubmit = async (event: FormEvent) => {
    event.preventDefault();
    if (!isFormValid || loading) return;
    setLoading(true);
    setError(null);

    try {
      const created = await createAnalysis({ projectGitUrl, projectName, branch, springVersionTarget });
      navigate(`/analyses/${created.id}`);
    } catch (err) {
      console.error(err);
      setError("Impossible de lancer l'analyse. Merci de vérifier les informations saisies.");
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="card">
      <h1>Lancer une nouvelle analyse</h1>
      <form onSubmit={handleSubmit}>
        <div>
          <label htmlFor="projectGitUrl">URL du dépôt Git</label>
          <input
            id="projectGitUrl"
            value={projectGitUrl}
            onChange={(e) => setProjectGitUrl(e.target.value)}
            placeholder="https://github.com/organisation/projet.git"
            required
          />
        </div>

        <div>
          <label htmlFor="projectName">Nom du projet</label>
          <input
            id="projectName"
            value={projectName}
            onChange={(e) => setProjectName(e.target.value)}
            placeholder="Mon application Spring"
            required
          />
        </div>

        <div>
          <label htmlFor="branch">Branche à analyser</label>
          <input
            id="branch"
            value={branch}
            onChange={(e) => setBranch(e.target.value)}
            placeholder="main"
            required
          />
        </div>

        <div>
          <label htmlFor="springVersionTarget">Version Spring cible</label>
          <select
            id="springVersionTarget"
            value={springVersionTarget}
            onChange={(e) => setSpringVersionTarget(e.target.value)}
          >
            {mockSpringTargets.map((version) => (
              <option key={version} value={version}>
                {version}
              </option>
            ))}
          </select>
        </div>

        <button type="submit" disabled={!isFormValid || loading}>
          {loading ? 'Analyse en cours...' : "Lancer l'analyse"}
        </button>
        {error && <p className="error">{error}</p>}
      </form>
    </div>
  );
}

export default ProjectSelectionPage;
