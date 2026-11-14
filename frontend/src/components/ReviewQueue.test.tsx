import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ApiError } from '../api/client';
import { fakeApi, hit, page } from '../test/fakeApi';
import { ReviewQueue } from './ReviewQueue';

async function rowFor(customerRef: string) {
  const cell = await screen.findByText(customerRef);
  return within(cell.closest('tr')!);
}

describe('ReviewQueue', () => {
  it('loads open hits newest-first from the API and shows their context', async () => {
    const api = fakeApi({ reviewQueue: vi.fn().mockResolvedValue(page([hit()])) });

    render(<ReviewQueue api={api} roles={['ANALYST']} />);

    const row = await rowFor('CUST-001');
    expect(row.getByText('Aleksandr Ivanovich Petrov')).toBeInTheDocument();
    expect(row.getByText('93%')).toBeInTheDocument();
    expect(row.getByText('v3')).toBeInTheDocument();
    expect(api.reviewQueue).toHaveBeenCalledWith('OPEN', 0, 20);
  });

  it('shows analyst actions on an OPEN hit', async () => {
    const api = fakeApi({ reviewQueue: vi.fn().mockResolvedValue(page([hit()])) });

    render(<ReviewQueue api={api} roles={['ANALYST']} />);

    const row = await rowFor('CUST-001');
    expect(row.getByRole('button', { name: 'Escalate' })).toBeInTheDocument();
    expect(row.getByRole('button', { name: 'Mark false positive' })).toBeInTheDocument();
    expect(row.queryByRole('button', { name: 'Confirm match' })).not.toBeInTheDocument();
  });

  it('shows no disposition buttons to a supervisor on an OPEN hit, but confirm on an ESCALATED one', async () => {
    const api = fakeApi({
      reviewQueue: vi.fn().mockResolvedValue(page([
        hit(),
        hit({ hitId: 'hit-2', customerRef: 'CUST-002', status: 'ESCALATED' }),
      ])),
    });

    render(<ReviewQueue api={api} roles={['SUPERVISOR']} />);

    const openRow = await rowFor('CUST-001');
    expect(openRow.queryByRole('button', { name: 'Escalate' })).not.toBeInTheDocument();
    expect(openRow.queryByRole('button', { name: 'Confirm match' })).not.toBeInTheDocument();
    const escalatedRow = await rowFor('CUST-002');
    expect(escalatedRow.getByRole('button', { name: 'Confirm match' })).toBeInTheDocument();
    expect(escalatedRow.getByRole('button', { name: 'Mark false positive' })).toBeInTheDocument();
  });

  it('requires a reason, sends the disposition and reloads the queue', async () => {
    const user = userEvent.setup();
    const reviewQueue = vi.fn()
      .mockResolvedValueOnce(page([hit()]))
      .mockResolvedValueOnce(page([]));
    const api = fakeApi({ reviewQueue, dispose: vi.fn().mockResolvedValue({}) });

    render(<ReviewQueue api={api} roles={['ANALYST']} />);
    await user.click((await rowFor('CUST-001')).getByRole('button', { name: 'Escalate' }));

    const confirm = screen.getByRole('button', { name: 'Confirm: Escalate' });
    expect(confirm).toBeDisabled();
    await user.type(screen.getByLabelText(/Reason for/), '   ');
    expect(confirm).toBeDisabled(); // whitespace-only reason is still blank

    await user.type(screen.getByLabelText(/Reason for/), 'Requires supervisor review');
    await user.click(confirm);

    expect(api.dispose).toHaveBeenCalledWith('hit-1', 'ESCALATED', 'Requires supervisor review');
    expect(await screen.findByRole('status')).toHaveTextContent('CUST-001: hit moved to Escalated.');
    expect(reviewQueue).toHaveBeenCalledTimes(2);
    expect(await screen.findByText('No open hits.')).toBeInTheDocument();
  });

  it('shows the server error when a disposition is rejected', async () => {
    const user = userEvent.setup();
    const api = fakeApi({
      reviewQueue: vi.fn().mockResolvedValue(page([hit()])),
      dispose: vi.fn().mockRejectedValue(
        new ApiError(409, 'CONCURRENT_MODIFICATION', 'The resource was modified by another request; reload and retry')),
    });

    render(<ReviewQueue api={api} roles={['ANALYST']} />);
    await user.click((await rowFor('CUST-001')).getByRole('button', { name: 'Mark false positive' }));
    await user.type(screen.getByLabelText(/Reason for/), 'Different DOB');
    await user.click(screen.getByRole('button', { name: 'Confirm: Mark false positive' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('modified by another request');
  });

  it('switches status tab and shows audit history for a hit', async () => {
    const user = userEvent.setup();
    const api = fakeApi({
      reviewQueue: vi.fn().mockResolvedValue(page([hit({ status: 'ESCALATED' })])),
      audit: vi.fn().mockResolvedValue([{
        auditId: 1, hitId: 'hit-1', fromStatus: 'OPEN', toStatus: 'ESCALATED', actor: 'analyst',
        role: 'ANALYST', reason: 'Requires supervisor review', timestamp: '2026-11-14T08:05:00Z',
      }]),
    });

    render(<ReviewQueue api={api} roles={['SUPERVISOR']} />);
    await user.click(screen.getByRole('tab', { name: 'Escalated' }));
    await waitFor(() => expect(api.reviewQueue).toHaveBeenLastCalledWith('ESCALATED', 0, 20));

    await user.click((await rowFor('CUST-001')).getByRole('button', { name: 'History' }));

    const history = await screen.findByRole('region', { name: 'Audit history' });
    expect(within(history).getByText('Open → Escalated')).toBeInTheDocument();
    expect(within(history).getByText('Requires supervisor review')).toBeInTheDocument();
    expect(api.audit).toHaveBeenCalledWith('hit-1');
  });
});
