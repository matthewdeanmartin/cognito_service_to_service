import { HttpClient, HttpHeaders } from "@angular/common/http";
import { Injectable } from "@angular/core";
import { Observable } from "rxjs";

export interface DemoResponse {
  user: string;
  primary: string;
  secondaryLabel: string;
}

@Injectable({ providedIn: "root" })
export class DemoApiService {
  private readonly baseUrl = "http://localhost:8081";

  constructor(private readonly http: HttpClient) {}

  callDemo(user: string): Observable<DemoResponse> {
    const headers = new HttpHeaders({
      "X-Demo-User": user
    });

    return this.http.get<DemoResponse>(`${this.baseUrl}/api/demo`, { headers });
  }
}
