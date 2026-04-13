import { describe, it, expect, vi } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import { SegmentedControl } from '../SegmentedControl'

describe('SegmentedControl', () => {
  it('renders 5 buttons with exact labels [б, н, у, сп, —]', () => {
    render(
      <SegmentedControl value="present" onValueChange={vi.fn()} />,
    )
    expect(screen.getByText('б')).toBeInTheDocument()
    expect(screen.getByText('н')).toBeInTheDocument()
    expect(screen.getByText('у')).toBeInTheDocument()
    expect(screen.getByText('сп')).toBeInTheDocument()
    expect(screen.getByText('—')).toBeInTheDocument()
  })

  it('each button is a <button type="button"> with role="radio" inside role="radiogroup"', () => {
    render(
      <SegmentedControl value="absent" onValueChange={vi.fn()} />,
    )
    const group = screen.getByRole('radiogroup')
    expect(group).toBeInTheDocument()

    const radios = screen.getAllByRole('radio')
    expect(radios).toHaveLength(5)
    radios.forEach((btn) => {
      expect(btn.tagName).toBe('BUTTON')
      expect(btn).toHaveAttribute('type', 'button')
    })
  })

  it('button matching value prop has aria-checked="true"; others have aria-checked="false"', () => {
    render(
      <SegmentedControl value="excused" onValueChange={vi.fn()} />,
    )
    expect(screen.getByLabelText('Уважительная причина')).toHaveAttribute('aria-checked', 'true')
    expect(screen.getByLabelText('Присутствовал')).toHaveAttribute('aria-checked', 'false')
    expect(screen.getByLabelText('Отсутствовал')).toHaveAttribute('aria-checked', 'false')
    expect(screen.getByLabelText('Свободное посещение')).toHaveAttribute('aria-checked', 'false')
    expect(screen.getByLabelText('Отменена')).toHaveAttribute('aria-checked', 'false')
  })

  it('clicking a button calls onValueChange with the correct AttendanceStatus', () => {
    const onValueChange = vi.fn()
    render(
      <SegmentedControl value="present" onValueChange={onValueChange} />,
    )
    fireEvent.click(screen.getByText('н'))
    expect(onValueChange).toHaveBeenCalledWith('absent')

    fireEvent.click(screen.getByText('сп'))
    expect(onValueChange).toHaveBeenCalledWith('free_attendance')
  })

  it('when disabled prop is true, onValueChange is NOT called on click', () => {
    const onValueChange = vi.fn()
    render(
      <SegmentedControl value="present" onValueChange={onValueChange} disabled />,
    )
    fireEvent.click(screen.getByText('н'))
    fireEvent.click(screen.getByText('у'))
    expect(onValueChange).not.toHaveBeenCalled()
  })
})
