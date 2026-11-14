import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ApiError } from '../api/client';
import { fakeApi } from '../test/fakeApi';
import { LoginForm } from './LoginForm';

async function signIn(username: string, password: string) {
  const user = userEvent.setup();
  await user.type(screen.getByLabelText('Username'), username);
  await user.type(screen.getByLabelText('Password'), password);
  await user.click(screen.getByRole('button', { name: 'Sign in' }));
}

describe('LoginForm', () => {
  it('verifies credentials with /api/me and passes the session on', async () => {
    const api = fakeApi({ me: vi.fn().mockResolvedValue({ username: 'analyst', roles: ['ANALYST'] }) });
    const createClient = vi.fn().mockReturnValue(api);
    const onLogin = vi.fn();
    render(<LoginForm onLogin={onLogin} createClient={createClient} />);

    await signIn('analyst', 'secret');

    expect(createClient).toHaveBeenCalledWith({ username: 'analyst', password: 'secret' });
    expect(onLogin).toHaveBeenCalledWith(api, { username: 'analyst', roles: ['ANALYST'] });
  });

  it('shows an error for wrong credentials', async () => {
    const api = fakeApi({ me: vi.fn().mockRejectedValue(new ApiError(401, 'UNAUTHORIZED', 'Authentication is required')) });
    const onLogin = vi.fn();
    render(<LoginForm onLogin={onLogin} createClient={() => api} />);

    await signIn('analyst', 'wrong');

    expect(await screen.findByRole('alert')).toHaveTextContent('Invalid username or password.');
    expect(onLogin).not.toHaveBeenCalled();
  });
});
