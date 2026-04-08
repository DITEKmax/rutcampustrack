import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { SidebarComponent } from '../sidebar/sidebar.component';
import { HeaderComponent } from '../header/header.component';

/**
 * Authenticated shell layout.
 *
 * Grid: [sidebar | (header + main)]. Sidebar is sticky full-height, header is
 * sticky at the top of the content column, main scrolls independently so the
 * header stays anchored while pages scroll. Page content itself is wrapped in
 * a centered max-width container so long tables don't stretch edge-to-edge.
 */
@Component({
  selector: 'app-shell',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterOutlet, SidebarComponent, HeaderComponent],
  templateUrl: './shell.component.html',
  styleUrl: './shell.component.css',
})
export class ShellComponent {}
