import { NavLink, Route, Routes } from 'react-router-dom';
import ProjectSelectionPage from './pages/ProjectSelectionPage';
import AnalysisListPage from './pages/AnalysisListPage';
import AnalysisDetailPage from './pages/AnalysisDetailPage';

function App() {
  return (
    <div>
      <nav className="navbar">
        <NavLink to="/" end>
          Nouvelle analyse
        </NavLink>
        <NavLink to="/analyses">Analyses</NavLink>
      </nav>
      <main className="container">
        <Routes>
          <Route path="/" element={<ProjectSelectionPage />} />
          <Route path="/analyses" element={<AnalysisListPage />} />
          <Route path="/analyses/:id" element={<AnalysisDetailPage />} />
        </Routes>
      </main>
    </div>
  );
}

export default App;
