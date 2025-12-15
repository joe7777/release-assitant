import { NavLink, Route, Routes } from 'react-router-dom';
import PromptRunnerPage from './pages/PromptRunnerPage';

function App() {
  return (
    <div>
      <nav className="navbar">
        <NavLink to="/" end>
          Prompt
        </NavLink>
      </nav>
      <main className="container">
        <Routes>
          <Route path="/" element={<PromptRunnerPage />} />
        </Routes>
      </main>
    </div>
  );
}

export default App;
