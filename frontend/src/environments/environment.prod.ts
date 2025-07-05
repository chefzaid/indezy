export const environment = {
  production: true,
  apiUrl: 'https://api.indezy.com/api',
  oauth: {
    google: {
      clientId: process.env['GOOGLE_CLIENT_ID'] || ''
    },
    github: {
      clientId: process.env['GITHUB_CLIENT_ID'] || ''
    },
    microsoft: {
      clientId: process.env['MICROSOFT_CLIENT_ID'] || ''
    }
  }
};
