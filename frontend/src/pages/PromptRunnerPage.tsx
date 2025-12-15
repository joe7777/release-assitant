import { FormEvent, useState } from 'react';
import { ChatRunResponse, runPrompt } from '../api/client';

function PromptRunnerPage() {
  const [prompt, setPrompt] = useState('Voici le lien de mon projet git ...');
  const [dryRun, setDryRun] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [result, setResult] = useState<ChatRunResponse | null>(null);

  const handleSubmit = async (event: FormEvent) => {
    event.preventDefault();
    if (!prompt.trim() || loading) return;
    setLoading(true);
    setError(null);

    try {
      const response = await runPrompt(prompt, dryRun);
      setResult(response);
    } catch (err) {
      console.error(err);
      setError("Impossible d'exécuter le prompt. Vérifiez le service llm-host.");
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="card">
      <h1>Prompt LLM (outil MCP)</h1>
      <form onSubmit={handleSubmit} className="prompt-form">
        <label htmlFor="prompt">Prompt</label>
        <textarea
          id="prompt"
          value={prompt}
          onChange={(e) => setPrompt(e.target.value)}
          rows={6}
          placeholder="Décrivez votre demande..."
        />

        <label className="checkbox">
          <input type="checkbox" checked={dryRun} onChange={(e) => setDryRun(e.target.checked)} />
          Mode DRY_RUN (plan sans exécution des tools)
        </label>

        <button type="submit" disabled={!prompt.trim() || loading}>
          {loading ? 'Exécution en cours...' : 'Lancer'}
        </button>
      </form>

      {error && <p className="error">{error}</p>}

      {result && (
        <div className="result">
          <h2>Réponse</h2>
          <pre className="output">{result.output}</pre>
          {result.structuredJson && (
            <div>
              <h3>Bloc JSON détecté</h3>
              <pre className="output">{result.structuredJson}</pre>
            </div>
          )}

          <h3>Traçage des tool-calls</h3>
          {result.toolCalls.length === 0 && <p>Aucun tool appelé (DRY_RUN ou réponse directe).</p>}
          {result.toolCalls.length > 0 && (
            <ul className="tool-calls">
              {result.toolCalls.map((call, index) => (
                <li key={`${call.toolName}-${index}`}>
                  <strong>{call.toolName}</strong> — {call.durationMs} ms — {call.success ? 'ok' : 'erreur'}
                  <div className="tool-args">{call.argumentsSummary}</div>
                  {call.errorMessage && <div className="error">{call.errorMessage}</div>}
                </li>
              ))}
            </ul>
          )}
        </div>
      )}
    </div>
  );
}

export default PromptRunnerPage;
