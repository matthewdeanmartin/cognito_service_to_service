import { Component } from "@angular/core";
import { CommonModule } from "@angular/common";
import { DemoApiService, DemoResponse } from "./demo-api.service";

@Component({
  selector: "app-root",
  standalone: true,
  imports: [CommonModule],
  templateUrl: "./app.component.html"
})
export class AppComponent {
  readonly username = "demo-user";

  loggedIn = false;
  result: DemoResponse | null = null;
  error: string | null = null;
  loading = false;

  constructor(private readonly api: DemoApiService) {}

  login(): void {
    this.loggedIn = true;
    this.result = null;
    this.error = null;
  }

  logout(): void {
    this.loggedIn = false;
    this.result = null;
    this.error = null;
  }

  callPrimary(): void {
    this.loading = true;
    this.result = null;
    this.error = null;

    this.api.callDemo(this.username).subscribe({
      next: (res) => {
        this.result = res;
        this.loading = false;
      },
      error: (err) => {
        this.error = `Call failed: ${err.status ? err.status + " " : ""}${err.message ?? err}`;
        this.loading = false;
      }
    });
  }
}
